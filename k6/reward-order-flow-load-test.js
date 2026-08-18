import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 배포된 실제 서버(게이트웨이) 대상 부하/스트레스 테스트.
//
// project-service/k6/reward-stock-load-test.js 는 /internal/v1/rewards/{id}/decrease-stock 를
// 직접 두드리지만, 그 경로는 설계상 게이트웨이 라우트가 없다(직접 Eureka-to-Eureka 전용,
// CLAUDE.md 참고) — 배포 서버에는 그 방식으로 도달할 수 없다.
// 그래서 이 스크립트는 실제 후원자가 쓰는 공개 흐름 그대로 POST /api/v1/orders 를 호출해
// order-service -> (Feign) -> project-service internal decrease-stock 체인 전체를 태운다.
// 수동 확인용 동일 흐름: http/backer-flow.http, http/creator-flow.http
//
// 알려진 한계 — 세밀한 실패 분류 불가:
// order-service의 RewardFeignClient.decreaseStock 은 FeignException 을 잡지 않고 그대로
// 던지고, OrderApiService.placeOrder 도 이를 다시 던지기만 한다(재고부족/락경합 여부와 무관).
// GlobalExceptionHandler 의 catch-all(Exception -> 500 "서버 오류가 발생했습니다.")이 이걸
// 받아버려서, project-service가 실제로 409(재고부족/락경합)를 반환했어도 클라이언트가 보는 건
// 그냥 500이다 — 로컬 스크립트처럼 메시지 텍스트로 재고부족/락경합/버그를 구분할 수 없다.
// 대신 상태코드별 카운터만 남긴다. 세밀한 분류가 필요하면 project-service 로그를 함께 봐야 한다.
//
// 사용 예:
//   MODE=measure VUS=100 DURATION=20s k6 run k6/reward-order-flow-load-test.js
//   MODE=stress RPS=2500 STRESS_DURATION=30s k6 run k6/reward-order-flow-load-test.js

const BASE_URL = __ENV.BASE_URL || 'https://earlybird-team5.duckdns.org';
const STOCK = parseInt(__ENV.STOCK || '1000000', 10);
const MODE = __ENV.MODE || 'measure'; // measure(RPS 미고정, 관찰용) | stress(RPS 고정)

const VUS = parseInt(__ENV.VUS || '100', 10);
const DURATION = __ENV.DURATION || '20s';

const RPS = parseInt(__ENV.RPS || '2000', 10);
const STRESS_DURATION = __ENV.STRESS_DURATION || '30s';
const PRE_ALLOCATED_VUS = parseInt(__ENV.PRE_ALLOCATED_VUS || String(Math.ceil(RPS / 5)), 10);
const MAX_VUS = parseInt(__ENV.MAX_VUS || String(PRE_ALLOCATED_VUS * 3), 10);

const CREATOR_EMAIL = __ENV.CREATOR_EMAIL || 'test@test.com';
const CREATOR_PASSWORD = __ENV.CREATOR_PASSWORD || '1234';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'test1@test.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || '1234';
const BACKER_EMAIL = __ENV.BACKER_EMAIL || 'livetest-backer-retry-1785376336108@earlybird.co.kr';
const BACKER_PASSWORD = __ENV.BACKER_PASSWORD || 'rawPassword1!';

// k6는 커스텀 메트릭을 init 컨텍스트에서만 선언할 수 있어(실행 중 동적 생성 불가),
// 나올 만한 상태코드를 미리 선언해두고 그 외는 status_other 로 모은다.
const successCount = new Counter('order_success');
const KNOWN_STATUSES = [200, 400, 401, 403, 404, 409, 422, 500, 502, 503];
const statusCounts = Object.fromEntries(
    KNOWN_STATUSES.map((s) => [s, new Counter(`order_status_${s}`)]),
);
const otherStatusCount = new Counter('order_status_other');
function statusCounter(status) {
    return statusCounts[status] || otherStatusCount;
}

export const options = {
    scenarios: MODE === 'stress'
        ? {
            stress: {
                executor: 'constant-arrival-rate',
                rate: RPS,
                timeUnit: '1s',
                duration: STRESS_DURATION,
                preAllocatedVUs: PRE_ALLOCATED_VUS,
                maxVUs: MAX_VUS,
            },
        }
        : {
            measure: {
                executor: 'constant-vus',
                vus: VUS,
                duration: DURATION,
            },
        },
};

function login(email, password) {
    const res = http.post(`${BASE_URL}/api/v1/users/login`, JSON.stringify({ email, password }), {
        headers: { 'Content-Type': 'application/json' },
    });
    const ok = check(res, { [`login ok (${email})`]: (r) => r.status === 200 });
    if (!ok) {
        throw new Error(`login failed for ${email}: status=${res.status} body=${res.body}`);
    }
    return {
        accessToken: res.json('data.accessToken'),
        userId: res.json('data.user.id'),
    };
}

function mustSucceed(res, label) {
    const ok = check(res, { [label]: (r) => r.status === 200 });
    if (!ok) {
        throw new Error(`${label} failed: status=${res.status} body=${res.body}`);
    }
    return res;
}

export function setup() {
    const creator = login(CREATOR_EMAIL, CREATOR_PASSWORD);
    const admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    const backer = login(BACKER_EMAIL, BACKER_PASSWORD);

    const creatorHeaders = {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${creator.accessToken}` },
    };
    const adminHeaders = { headers: { Authorization: `Bearer ${admin.accessToken}` } };

    const categoryRes = mustSucceed(
        http.get(`${BASE_URL}/api/v1/project-categories`, creatorHeaders),
        'categories fetched',
    );
    if (!(categoryRes.json('data').length > 0)) {
        throw new Error(`no categories found: ${categoryRes.body}`);
    }
    const categoryId = categoryRes.json('data.0.id');

    const now = new Date();
    const endAt = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    const projectRes = mustSucceed(http.post(`${BASE_URL}/api/v1/projects`, JSON.stringify({
        title: `k6 ${MODE} load test ${Date.now()}`,
        categoryId,
        summary: 'reward stock load test against deployed server',
        description: 'k6 reward-order-flow-load-test.js 로 생성된 프로젝트',
        goalAmount: 1000000,
        startAt: now.toISOString().slice(0, 19),
        endAt,
    }), creatorHeaders), 'project created');
    const projectId = projectRes.json('data.projectId');

    mustSucceed(http.post(`${BASE_URL}/api/v1/projects/${projectId}/approve`, null, adminHeaders), 'project approved');

    const rewardRes = mustSucceed(http.post(`${BASE_URL}/api/v1/projects/${projectId}/rewards`, JSON.stringify({
        name: `k6 ${MODE} reward`,
        description: '부하테스트용 한정수량 리워드',
        price: 10000,
        totalQuantity: STOCK,
    }), creatorHeaders), 'reward created');
    const rewardId = rewardRes.json('data.rewardId');
    const price = rewardRes.json('data.price');

    console.log(`[setup] mode=${MODE} projectId=${projectId} rewardId=${rewardId} stock=${STOCK} price=${price}`);

    return {
        rewardId,
        price,
        backerToken: backer.accessToken,
        backerUserId: backer.userId,
    };
}

export default function (data) {
    const shippingFee = data.price >= 50000 ? 0 : 3000;
    const res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({
        userId: data.backerUserId,
        requests: [{ rewardId: data.rewardId, quantity: 1, expectedUnitPrice: data.price }],
        receiverName: 'k6 load test',
        receiverPhone: '010-0000-0000',
        shippingAddress: 'k6 load test address',
        zipCode: '00000',
        expectedItemsAmount: data.price,
        expectedTotalAmount: data.price + shippingFee,
    }), {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.backerToken}` },
    });

    statusCounter(res.status).add(1);
    if (res.status === 200) {
        successCount.add(1);
    }
}

export function teardown(data) {
    const rewardRes = http.get(`${BASE_URL}/api/v1/rewards/${data.rewardId}`);
    const remainingQuantity = rewardRes.json('data.remainingQuantity');
    console.log(`[teardown] rewardId=${data.rewardId} remainingQuantity=${remainingQuantity} `
        + `(STOCK=${STOCK} 이면 STOCK - remainingQuantity 를 order_success 누적값과 대조)`);
}
