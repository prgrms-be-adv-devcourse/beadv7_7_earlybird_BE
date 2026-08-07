import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 설계 문서: docs/superpowers/specs/2026-07-31-reward-stock-k6-load-test-design.md
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const STOCK = parseInt(__ENV.STOCK || '300', 10);
const VUS = parseInt(__ENV.VUS || '100', 10);
const DURATION = __ENV.DURATION || '20s';

const successCount = new Counter('reward_decrease_success');
const outOfStockCount = new Counter('reward_decrease_out_of_stock');
const lockConflictCount = new Counter('reward_decrease_lock_conflict');
const unexpectedCount = new Counter('reward_decrease_unexpected');

export const options = {
    scenarios: {
        flash_sale: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
        },
    },
};

export function setup() {
    const creatorHeaders = {
        headers: {
            'Content-Type': 'application/json',
            'X-User-Id': '1',
            'X-User-Role': 'CREATOR',
        },
    };
    const adminHeaders = {
        headers: {
            'Content-Type': 'application/json',
            'X-User-Role': 'ADMIN',
        },
    };

    const endAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    const projectRes = http.post(`${BASE_URL}/api/v1/projects`, JSON.stringify({
        title: 'k6 flash sale load test',
        categoryId: 1,
        summary: 'reward stock decrement load test',
        description: 'k6 부하테스트용 프로젝트',
        goalAmount: 1000000,
        startAt: new Date().toISOString().slice(0, 19),
        endAt,
    }), creatorHeaders);
    check(projectRes, { 'project created': (r) => r.status === 200 });
    const projectId = projectRes.json('data.projectId');

    const approveRes = http.post(`${BASE_URL}/api/v1/projects/${projectId}/approve`, null, adminHeaders);
    check(approveRes, { 'project approved': (r) => r.status === 200 });

    const rewardRes = http.post(`${BASE_URL}/api/v1/projects/${projectId}/rewards`, JSON.stringify({
        name: 'k6 flash sale reward',
        description: '한정수량 리워드',
        price: 10000,
        totalQuantity: STOCK,
    }), creatorHeaders);
    check(rewardRes, { 'reward created': (r) => r.status === 200 });
    const rewardId = rewardRes.json('data.rewardId');

    console.log(`[setup] projectId=${projectId} rewardId=${rewardId} stock=${STOCK}`);
    return { rewardId };
}

export default function (data) {
    const res = http.post(
        `${BASE_URL}/internal/v1/rewards/${data.rewardId}/decrease-stock`,
        JSON.stringify({ quantity: 1 }),
        { headers: { 'Content-Type': 'application/json' } },
    );

    if (res.status === 200) {
        successCount.add(1);
        return;
    }

    if (res.status === 409) {
        const message = res.json('error.message') || '';
        if (message.includes('재고가 부족합니다')) {
            outOfStockCount.add(1);
        } else if (message.includes('동시 수정 충돌')) {
            lockConflictCount.add(1);
        } else {
            unexpectedCount.add(1);
        }
        return;
    }

    unexpectedCount.add(1);
}

export function teardown(data) {
    const rewardRes = http.get(`${BASE_URL}/api/v1/rewards/${data.rewardId}`);
    const remainingQuantity = rewardRes.json('data.remainingQuantity');
    console.log(`[teardown] rewardId=${data.rewardId} remainingQuantity=${remainingQuantity} (STOCK=${STOCK} 이면 정합성은 STOCK - remainingQuantity == reward_decrease_success 누적값과 비교해서 확인)`);
}
