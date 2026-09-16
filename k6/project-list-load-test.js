import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 프로젝트 목록 조회 API 부하테스트
//
// PR(강대혁/project/list-pagination, BE #790) 머지 전후 성능 비교용.
// 핵심 변경사항:
//   - 목록 전용 응답 DTO로 description 제거
//   - DB LIMIT/OFFSET 페이징 적용(전체 행 로드 → page size만큼만)
//
// 실측(배포 서버): 148,034 bytes(86건 전부 + description) → 3,403 bytes(8건 페이지) = 43배 감소.
//   size=100으로 한 번에 받으면 40,878 bytes (2026-09-14 브라우저 Resource Timing 재확인).
//   VU별 곡선: 1명 39 req/s, 10명 170, 30명 249(포화), 100명 245 — 30명에서 처리량 상한.
//
// 사용 예:
//   # 기본 (VU 100, 30초)
//   k6 run k6/project-list-load-test.js
//
//   # 환경변수로 조절
//   BASE_URL=https://earlybird-team5.duckdns.org VUS=200 DURATION=60s k6 run k6/project-list-load-test.js
//
//   # 키워드 검색 포함
//   KEYWORD=스마트 k6 run k6/project-list-load-test.js

const BASE_URL = __ENV.BASE_URL || 'https://earlybird-team5.duckdns.org';
const VUS = parseInt(__ENV.VUS || '100', 10);
const DURATION = __ENV.DURATION || '30s';
// 키워드를 지정하면 ES 검색 경로도 함께 측정된다.
const KEYWORD = __ENV.KEYWORD || '';

// 상태코드별 카운터
const successCount = new Counter('list_success');
const errorCount = new Counter('list_error');
const KNOWN_STATUSES = [200, 400, 401, 403, 500, 502, 503];
const statusCounts = Object.fromEntries(
    KNOWN_STATUSES.map((s) => [s, new Counter(`list_status_${s}`)]),
);
const otherStatusCount = new Counter('list_status_other');
function statusCounter(status) {
    return statusCounts[status] || otherStatusCount;
}

// 응답 크기 & 레이턴시 분포
const responseSizeTrend = new Trend('list_response_bytes');
const latencyTrend = new Trend('list_latency_ms');

export const options = {
    scenarios: {
        list_query: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
        },
    },
    thresholds: {
        // P95 응답시간 1초 이하 목표
        list_latency_ms: ['p(95)<1000'],
    },
};

export default function () {
    // 페이지를 0~4 사이로 랜덤하게 뒤섞어 캐시 히트 편향을 줄인다.
    const page = Math.floor(Math.random() * 5);
    const size = 8; // 기본값(PAGE_SIZE = 8)과 동일하게

    let url = `${BASE_URL}/api/v1/projects?page=${page}&size=${size}`;
    if (KEYWORD) {
        url += `&keyword=${encodeURIComponent(KEYWORD)}`;
    }

    const res = http.get(url);

    statusCounter(res.status).add(1);
    latencyTrend.add(res.timings.duration);
    responseSizeTrend.add(res.body ? res.body.length : 0);

    const ok = check(res, {
        'status 200': (r) => r.status === 200,
        'has content array': (r) => {
            try {
                const body = JSON.parse(r.body);
                return Array.isArray(body.data?.content);
            } catch {
                return false;
            }
        },
        'no description field': (r) => {
            // PR 변경 검증: 목록 응답에 description이 없어야 한다
            try {
                const body = JSON.parse(r.body);
                const items = body.data?.content || [];
                return items.every((item) => item.description === undefined);
            } catch {
                return false;
            }
        },
    });

    if (ok && res.status === 200) {
        successCount.add(1);
    } else {
        errorCount.add(1);
    }
}
