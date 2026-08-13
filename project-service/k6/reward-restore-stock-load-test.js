import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// reward-stock-load-test.js의 반대 방향 시나리오: 정산(settlement-service)이 실패한 프로젝트를
// 일괄 환불할 때, 같은 리워드를 산 여러 백커의 restoreStock이 동시에 같은 행에 몰리는 상황을 재현한다.
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const STOCK = parseInt(__ENV.STOCK || '300', 10);
const VUS = parseInt(__ENV.VUS || '100', 10);
const DURATION = __ENV.DURATION || '20s';

const successCount = new Counter('reward_restore_success');
const overflowCount = new Counter('reward_restore_overflow');
const lockConflictCount = new Counter('reward_restore_lock_conflict');
const unexpectedCount = new Counter('reward_restore_unexpected');

const successDuration = new Trend('reward_restore_success_duration');
const overflowDuration = new Trend('reward_restore_overflow_duration');
const lockConflictDuration = new Trend('reward_restore_lock_conflict_duration');

export const options = {
    scenarios: {
        batch_refund: {
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
        title: 'k6 batch refund load test',
        categoryId: 1,
        summary: 'reward stock restore load test',
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
        name: 'k6 batch refund reward',
        description: '전량 판매 완료 후 일괄 환불 대상 리워드',
        price: 10000,
        totalQuantity: STOCK,
    }), creatorHeaders);
    check(rewardRes, { 'reward created': (r) => r.status === 200 });
    const rewardId = rewardRes.json('data.rewardId');

    // "전량 판매 완료" 상태를 만든다 — 이후 부하 단계에서 이만큼을 동시에 복원한다.
    // orderId=0은 부하 단계(orderId = __VU*1000000+__ITER, __VU>=1)와 절대 겹치지 않는 setup 전용 값.
    const sellOutRes = http.post(
        `${BASE_URL}/internal/v1/rewards/${rewardId}/decrease-stock`,
        JSON.stringify({ quantity: STOCK, orderId: 0 }),
        { headers: { 'Content-Type': 'application/json' } },
    );
    check(sellOutRes, { 'sold out for setup': (r) => r.status === 200 });

    console.log(`[setup] projectId=${projectId} rewardId=${rewardId} stock=${STOCK} (전량 판매 완료 상태로 시작, 원자적 UPDATE 버전)`);
    return { rewardId };
}

export default function (data) {
    // decrease 스크립트와 같은 이유로 orderId를 VU/ITER 조합으로 매번 다르게 보낸다(#195 멱등키).
    const orderId = __VU * 1000000 + __ITER;
    const res = http.post(
        `${BASE_URL}/internal/v1/rewards/${data.rewardId}/restore-stock`,
        JSON.stringify({ quantity: 1, orderId }),
        { headers: { 'Content-Type': 'application/json' } },
    );

    if (res.status === 200) {
        successCount.add(1);
        successDuration.add(res.timings.duration);
        return;
    }

    if (res.status === 409) {
        const message = res.json('error.message') || '';
        if (message.includes('초과할 수 없습니다')) {
            overflowCount.add(1);
            overflowDuration.add(res.timings.duration);
        } else if (message.includes('동시 수정 충돌')) {
            lockConflictCount.add(1);
            lockConflictDuration.add(res.timings.duration);
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
    console.log(`[teardown] rewardId=${data.rewardId} remainingQuantity=${remainingQuantity} (STOCK=${STOCK} 이면 정합성은 remainingQuantity == STOCK 이고 reward_restore_success 누적값도 STOCK과 같아야 함)`);
}
