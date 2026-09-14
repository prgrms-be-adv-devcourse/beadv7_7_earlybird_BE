import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 배포된 실제 서버(게이트웨이) 대상 부하/스트레스 테스트.
//
// project-service/k6/reward-stock-load-test.js 는 /internal/v1/rewards/{id}/decrease-stock 를
// 직접 두드리지만, 그 경로는 설계상 게이트웨이 라우트가 없다(직접 Eureka-to-Eureka 전용,
// CLAUDE.md 참고) — 배포 서버에는 그 방식으로 도달할 수 없다.
// 그래서 이 스크립트는 실제 후원자가 쓰는 공개 흐름 그대로 장바구니 담기 -> POST /api/v1/orders 를
// 호출해 order-service -> (Feign) -> project-service decrease-stock 체인 전체를 태운다.
// 수동 확인용 동일 흐름: http/backer-flow.http, http/creator-flow.http
//
// 주문 API 제약 두 가지 때문에 후원자 계정을 VU마다 따로 만든다:
//   1) orderIdempotencyKey 필수 — 요청마다 새 UUID
//   2) 주문 내용이 장바구니와 정확히 일치해야 한다 — 계정 하나를 VU들이 공유하면 카트가 서로 덮여
//      재고 경합이 아니라 카트 불일치로 실패한다.
// setup 에서 계정·프로젝트·리워드를 새로 만들어 배포 DB에 테스트 데이터가 쌓인다(이메일 k6*-{timestamp}).
//
// 사용 예:
//   MODE=measure VUS=50 DURATION=40s k6 run k6/reward-order-flow-load-test.js
//   MODE=stress RPS=50 STRESS_DURATION=30s k6 run k6/reward-order-flow-load-test.js

const BASE_URL = __ENV.BASE_URL || 'https://earlybird-team5.duckdns.org';
const STOCK = parseInt(__ENV.STOCK || '1000000', 10);
const MODE = __ENV.MODE || 'measure'; // measure(RPS 미고정, 관찰용) | stress(RPS 고정)

const VUS = parseInt(__ENV.VUS || '50', 10);
const DURATION = __ENV.DURATION || '20s';

const RPS = parseInt(__ENV.RPS || '50', 10);
const STRESS_DURATION = __ENV.STRESS_DURATION || '30s';
const PRE_ALLOCATED_VUS = parseInt(__ENV.PRE_ALLOCATED_VUS || String(Math.ceil(RPS / 2)), 10);
const MAX_VUS = parseInt(__ENV.MAX_VUS || String(PRE_ALLOCATED_VUS * 3), 10);
const BACKERS = MODE === 'stress' ? MAX_VUS : VUS;

const PASSWORD = 'rawPassword1!';

// k6는 커스텀 메트릭을 init 컨텍스트에서만 선언할 수 있어(실행 중 동적 생성 불가),
// 나올 만한 상태코드를 미리 선언해두고 그 외는 status_other 로 모은다.
const successCount = new Counter('order_success');
const KNOWN_STATUSES = [200, 400, 401, 403, 404, 409, 422, 500, 502, 503];
const statusCounts = Object.fromEntries(
    KNOWN_STATUSES.map((s) => [s, new Counter(`order_status_${s}`)]),
);
const otherStatusCount = new Counter('order_status_other');
const cartFailCount = new Counter('cart_add_failed');
const orderLatency = new Trend('order_latency', true);

export const options = {
    setupTimeout: '5m',
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

function params(token) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    return { headers };
}

function mustSucceed(res, label) {
    const ok = check(res, { [label]: (r) => r.status === 200 });
    if (!ok) {
        throw new Error(`${label} failed: status=${res.status} body=${res.body}`);
    }
    return res;
}

function signupRequest(email) {
    return ['POST', `${BASE_URL}/api/v1/users/signup`, JSON.stringify({
        email, password: PASSWORD, name: 'k6', phoneNumber: '010-0000-0002',
    }), params()];
}

function loginRequest(email) {
    return ['POST', `${BASE_URL}/api/v1/users/login`, JSON.stringify({ email, password: PASSWORD }), params()];
}

function login(email) {
    const res = mustSucceed(http.request(...loginRequest(email)), `login (${email})`);
    return { token: res.json('data.accessToken'), userId: res.json('data.user.id') };
}

// role 은 토큰에 실리므로 전환 후 다시 로그인해야 반영된다.
function accountWithRole(email, role) {
    http.request(...signupRequest(email));
    mustSucceed(http.post(`${BASE_URL}/api/v1/users/me/role`, JSON.stringify({ role }), params(login(email).token)),
        `role ${role}`);
    return login(email).token;
}

// 후원자 수백 명을 순차 가입시키면 setup 이 수 분 걸려서 50개씩 병렬로 보낸다.
function createBackers(stamp) {
    const emails = Array.from({ length: BACKERS }, (_, i) => `k6b-${stamp}-${i}@earlybird.co.kr`);
    const backers = [];
    for (let i = 0; i < emails.length; i += 50) {
        const chunk = emails.slice(i, i + 50);
        http.batch(chunk.map(signupRequest));
        http.batch(chunk.map(loginRequest)).forEach((res, j) => {
            mustSucceed(res, `backer login (${chunk[j]})`);
            backers.push({ token: res.json('data.accessToken'), userId: res.json('data.user.id') });
        });
    }
    return backers;
}

export function setup() {
    const stamp = Date.now();
    const creatorToken = accountWithRole(`k6c-${stamp}@earlybird.co.kr`, 'CREATOR');
    const adminToken = accountWithRole(`k6a-${stamp}@earlybird.co.kr`, 'ADMIN');

    const categoryRes = mustSucceed(http.get(`${BASE_URL}/api/v1/project-categories`, params(creatorToken)),
        'categories fetched');
    if (!(categoryRes.json('data').length > 0)) {
        throw new Error(`no categories found: ${categoryRes.body}`);
    }

    const now = new Date();
    const projectRes = mustSucceed(http.post(`${BASE_URL}/api/v1/projects`, JSON.stringify({
        title: `k6 ${MODE} load test ${stamp}`,
        categoryId: categoryRes.json('data.0.id'),
        summary: 'reward stock load test against deployed server',
        description: 'k6 reward-order-flow-load-test.js 로 생성된 프로젝트',
        goalAmount: 1000000,
        startAt: now.toISOString().slice(0, 19),
        endAt: new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
    }), params(creatorToken)), 'project created');
    const projectId = projectRes.json('data.projectId');

    mustSucceed(http.post(`${BASE_URL}/api/v1/projects/${projectId}/approve`, null, params(adminToken)),
        'project approved');

    const rewardRes = mustSucceed(http.post(`${BASE_URL}/api/v1/projects/${projectId}/rewards`, JSON.stringify({
        name: `k6 ${MODE} reward`,
        description: '부하테스트용 한정수량 리워드',
        price: 10000,
        totalQuantity: STOCK,
    }), params(creatorToken)), 'reward created');

    const backers = createBackers(stamp);
    const rewardId = rewardRes.json('data.rewardId');
    console.log(`[setup] mode=${MODE} projectId=${projectId} rewardId=${rewardId} stock=${STOCK} backers=${backers.length}`);

    return { projectId, rewardId, price: rewardRes.json('data.price'), backers };
}

export default function (data) {
    const backer = data.backers[(__VU - 1) % data.backers.length];
    const auth = params(backer.token);

    // 이전 반복의 카트 잔여물 제거 후 1개만 담는다 — 주문은 카트 내용과 정확히 일치해야 한다.
    http.del(`${BASE_URL}/api/v1/carts/items/${data.rewardId}`, null, auth);
    const added = http.post(`${BASE_URL}/api/v1/carts/items`, JSON.stringify({
        projectId: data.projectId, items: [{ rewardId: data.rewardId, quantity: 1 }],
    }), auth);
    if (added.status !== 200) {
        cartFailCount.add(1);
        return;
    }

    const shippingFee = data.price >= 50000 ? 0 : 3000;
    const res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({
        userId: backer.userId,
        projectId: data.projectId,
        orderIdempotencyKey: crypto.randomUUID(),
        requests: [{ rewardId: data.rewardId, quantity: 1, expectedUnitPrice: data.price }],
        receiverName: 'k6 load test',
        receiverPhone: '010-0000-0000',
        shippingAddress: 'k6 load test address',
        zipCode: '00000',
        expectedItemsAmount: data.price,
        expectedTotalAmount: data.price + shippingFee,
    }), auth);

    (statusCounts[res.status] || otherStatusCount).add(1);
    orderLatency.add(res.timings.duration);
    if (res.status === 200) {
        successCount.add(1);
    } else if (__ITER < 2) {
        console.log(`[order ${res.status}] ${res.body}`);
    }
}

export function teardown(data) {
    const rewardRes = http.get(`${BASE_URL}/api/v1/rewards/${data.rewardId}`);
    console.log(`[teardown] rewardId=${data.rewardId} remainingQuantity=${rewardRes.json('data.remainingQuantity')} `
        + `(STOCK - remainingQuantity 를 order_success 누적값과 대조)`);
}
