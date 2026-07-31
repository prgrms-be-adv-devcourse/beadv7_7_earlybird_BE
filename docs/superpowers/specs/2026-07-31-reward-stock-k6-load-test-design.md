# Reward 재고 차감 k6 부하 테스트 설계

## 배경

project-service엔 이미 낙관적 락(`@Version`) 경합을 검증하는 JUnit 통합테스트
(`RewardConcurrencyIntegrationTest` 등)가 있지만, 이건 JVM 내부에서 스레드 몇 개로
"데이터가 안 깨지는지"만 확인할 뿐 실제 HTTP를 통한 처리량(TPS)·지연시간은 재지 않는다.

project-service 안에서 동시성이 걸린 지점은 4곳이다:
1. Reward 재고 차감(`decreaseStock`) — 실제 후원 트래픽 패턴, 진짜 핫패스
2. Project 마감 확정(`closeProjectByDeadline`) — 저빈도 배치/관리자 작업
3. Project 삭제 vs Reward 재고차감 — 데드락 방지 검증(처리량과 무관)
4. Category 부모변경/삭제 — JVM 락, 저빈도 관리자 작업

이 중 TPS라는 지표가 의미 있는 곳은 1번뿐이다. 나머지는 "동시에 때리면 정합성이
깨지는가"를 보는 1회성 경합 테스트이고 이미 JUnit으로 커버되어 있어, k6로 부하를
걸어도 얻는 정보가 적다. 이번 설계는 **1번(Reward 재고 차감) 하나에 집중**한다.

## 목표

한정 수량 리워드에 다수의 후원자가 동시에 구매를 시도하는 "플래시세일" 패턴을
실제 HTTP 요청으로 재현하고, TPS·지연시간·성공/실패율을 측정한다. 테스트가 끝난
뒤 남은 재고가 실제 성공 건수와 정확히 일치하는지 확인해, lost update가 없었음을
JUnit과는 별개로 실측 트래픽으로도 재확인한다.

## 대상 & 실행 환경

- 대상: `POST /internal/v1/rewards/{rewardId}/decrease-stock`
- project-service를 로컬 `:8081`에 직접 띄우고 호출한다. `/internal/**`은 설계상
  게이트웨이 라우트가 없으므로(직접 Eureka-to-Eureka 호출 전용) 이 경로가 맞다.
- 실행 순서: config-server → discovery-server → project-service. gateway-server는
  필요 없다.
- project-service는 `X-User-Id`/`X-User-Role` 헤더를 자체적으로 신뢰하고 JWT를
  검증하지 않으므로(게이트웨이가 검증을 대신함), k6가 이 헤더를 직접 세팅해
  프로젝트 등록/승인/리워드 등록에 사용한다. `/internal/**`(decrease-stock 본체)은
  이 헤더가 필요 없다.
- 도구: k6. 스크립트 위치: `project-service/k6/reward-stock-load-test.js`

**order-service는 우회한다.** 실제 운영에서는 order-service가 후원(주문) 생성 시
`RewardFeignClient`를 통해 이 내부 API를 호출하지만(`RewardInternalController`
주석 참고), 이번 테스트는 k6가 그 호출자 역할을 대신해 `decrease-stock`을 직접
두드린다. order-service까지 태우면 실제 주문 생성 → 결제(payment-service, 현재
스텁 구현) → reward 차감까지 체인이 늘어나서 "재고 차감 자체의 처리량"이 아니라
체인 전체의 병목(특히 스텁 payment 지연)이 섞여 순수 TPS를 재기 어려워진다. 또한
order-service는 project-service 스코프 밖(다른 팀원 담당)이라 이번 작업에서
건드리지 않는다.

## 테스트 데이터 준비 (`setup()`)

1. `POST /api/v1/projects` (`X-User-Id: 1`, `X-User-Role: CREATOR`) —
   `categoryId=1`(시드 카테고리), `endAt`은 충분히 미래로 설정
2. `POST /api/v1/projects/{id}/approve` (`X-User-Role: ADMIN`) — `decreaseStock`이
   "진행중(open) 프로젝트"만 허용하므로 승인 필수
3. `POST /api/v1/projects/{id}/rewards` (`X-User-Role: CREATOR`) —
   `totalQuantity = STOCK`(환경변수, 기본 300)
4. 이후 부하 단계에서 쓸 `rewardId`를 반환

## 부하 프로파일

환경변수로 조정 가능, 기본값:

- `STOCK=300` (리워드 초기 재고)
- `VUS=100` (동시 가상 사용자 수)
- `DURATION=20s`
- `BASE_URL=http://localhost:8081`

k6 `constant-vus` executor로 VUS개 VU가 DURATION 동안 쉬지 않고
`decrease-stock(quantity=1)`을 반복 호출한다. 총 요청 수는 재고보다 훨씬 많아지도록
설계해, 테스트 내내 "성공"과 "재고소진/락경합 실패"가 섞여서 발생하게 한다 — 실제
한정수량 캠페인의 경합 패턴과 동일하다.

## 측정 지표

- **TPS/지연시간**: k6 기본 제공 지표 그대로 사용 — `http_reqs`(rate = TPS),
  `http_req_duration`(p95/p99)
- **커스텀 카운터**: 성공(200) / 재고부족·락재시도소진(409, 둘 다
  `IllegalStateException` → 409로 응답 상태코드는 동일하므로 응답 메시지 텍스트로
  구분) / 그 외 예상 못한 상태코드(버그 의심)

## 정합성 검증

`teardown()`에서 `GET /api/v1/rewards/{rewardId}`를 한 번 호출해 최종
`remainingQuantity`를 콘솔에 출력한다. `STOCK - remainingQuantity`는 부하 단계에서
집계된 성공 카운터와 정확히 같아야 한다(다르면 lost update 버그).

k6는 커스텀 메트릭의 누적값을 스크립트 코드에서 직접 읽어올 방법이 없어(쓰기 전용
API), `handleSummary()` 안에서 이 둘을 자동 비교해 pass/fail을 만드는 건 k6 실행
모델과 맞지 않는 억지 구조가 된다. 대신 **성공 카운터 값과 teardown의
remainingQuantity를 실행 후 로그로 나란히 출력**하고, 실행한 사람이 눈으로
대조한다 — 기존 JUnit 테스트의 `assertThat` 역할을 여기선 리포트 비교로 대체한다.

## 범위 밖

- 2~4번 동시성 지점(Project 마감확정, Project삭제 vs Reward재고차감, Category
  락)은 이번 스크립트에 포함하지 않는다. 이미 JUnit 통합테스트로 정합성이
  검증되어 있고, 저빈도 관리자 작업이라 "TPS"라는 지표 자체가 의미가 없다.
- 배포된 실제 환경(EC2 등) 대상 실행은 이번 범위가 아니다. 로컬 서버만 대상으로
  한다.
