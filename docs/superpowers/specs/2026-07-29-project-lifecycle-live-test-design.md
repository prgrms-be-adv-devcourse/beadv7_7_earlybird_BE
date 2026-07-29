# 배포 환경 project-service 라이프사이클 라이브 테스트 설계

- 날짜: 2026-07-29
- 담당: 강대혁 (project-service)
- 배경: 실제 배포 환경(`https://earlybird-team5-api.duckdns.org`, EC2 + Caddy, `main` 브랜치 CD 결과물)에서 project-service의 API가 order-service와 실제로 정상 상호작용하는지 — 특히 fundedAmount 반영과 프로젝트 상태 전이(마감 배치/조기종료/취소) — 검증할 방법이 없었다.

## 경위 / 조사 결과

- 기존에 PR #158로 `system-test` 모듈이 이미 만들어져 있다 — `@Tag("live")` JUnit 테스트가 순수 `java.net.http.HttpClient`로 게이트웨이(`-Dsystem-test.baseUrl`로 대상 지정, 기본값 로컬)를 직접 호출한다. `CreatorFlowLiveTest`(가입→창작자전환→프로젝트생성→리워드등록→관리자승인), `BackerFlowLiveTest`(가입→로그인→조회→주문생성→취소)가 있지만, 둘 다 "생성이 성공했다"까지만 확인하고 **주문이 project의 fundedAmount에 실제로 반영되는지, 프로젝트 상태 전이가 올바른지는 검증하지 않는다.**
- fundedAmount 동기화(PR #137, pull 방식, 1분 주기)는 이미 `develop`과 `main` 양쪽에 병합·배포되어 있다 — 이번 테스트가 겨냥할 실제 동작 대상이다.
- 처음엔 settlement-service와의 상호작용(정산 실행이 프로젝트 상태를 pull하는지)도 검증 범위로 논의했으나, 조사 결과:
  - project-service는 이미 `GET /internal/v1/projects?status=SUCCEEDED`를 제공하고, settlement-service의 `ProjectSettlementTargetHttpReader` → `ProjectSettlementRunService.run()`이 이미 이걸 정상 호출한다 — **연동 자체는 되어 있다.**
  - 진짜 갭은 "누가 `run()`을 자동으로 트리거하냐"였다 — settlement-service엔 `@Scheduled`가 전혀 없고, 유일한 트리거(`POST /internal/v1/settlements/runs`)는 `/internal/v1/**`라 게이트웨이 라우트가 없어 외부에서 호출 불가능하다.
  - settlement팀에 문의한 결과, **시간 관계상 자동 배치 대신 수동 테스트용 API로만 유지하기로 팀 결정**이 났다 — 버그가 아니라 의도된 범위 축소다. 따라서 **이번 라이브 테스트에서 settlement 연동 검증은 범위에서 제외**한다.
- 마감 배치(`ProjectDeadlineScheduler`, 매일 자정 Asia/Seoul)를 실제 자정까지 기다리지 않고 확인하는 방법: `ProjectController`에 이미 `POST /api/v1/projects/close-expired`(관리자 전용, "테스트/운영 확인용으로 즉시 트리거"라고 주석에 명시된 기존 엔드포인트)가 있다 — 그대로 재사용.
- `Project.validatePeriod()`는 "endAt이 startAt 이후 & 3개월 이내"만 검증하고 "오늘 이후"는 검증하지 않는다 — 그래서 이미 지난 `endAt`으로 프로젝트를 바로 생성할 수 있다. 자정을 기다릴 필요 없이 `close-expired`를 바로 호출해 결과를 즉시 확인 가능.

## API 검증 결과

라이브 테스트가 호출할 API들을 실제 코드(project-service 컨트롤러 + gateway `SecurityConfig`)와 대조한 결과. ✅는 문서/코드 일치, **이상함**은 불일치·부정확한 항목.

| # | API | 검증 |
|---|---|---|
| 1 | POST `/api/v1/projects` | ✅ |
| 2 | GET `/api/v1/projects` | 이상함 — 게이트웨이는 `permitAll`(로그인 없이도 호출 가능)인데 "X-User-Role 필요"처럼 표기됨. 헤더 없으면 컨트롤러가 BACKER로 간주 |
| 3 | GET `/api/v1/projects/me` | ✅ (게이트웨이 `hasRole(CREATOR)`) |
| 4 | GET `/api/v1/projects/{id}` | ✅ (permitAll) |
| 5 | PATCH `/api/v1/projects/{id}` | ✅ (게이트웨이 `hasRole(CREATOR)`, 본인 확인은 서비스 레이어) |
| 6 | DELETE `/api/v1/projects/{id}` | ✅ |
| 7 | POST `/api/v1/projects/{id}/cancel` | **이상함** — 게이트웨이에 이 경로 규칙이 아예 없어 catch-all(`authenticated()`)로 빠짐. "본인/ADMIN" 검증이 게이트웨이가 아니라 서비스 레이어(`validateOwnershipOrAdmin`)에서만 이뤄짐 |
| 8 | POST `/api/v1/projects/{id}/approve` | ✅ |
| 9 | POST `/api/v1/projects/{id}/reject` | ✅ |
| 10 | PATCH `/api/v1/projects/{id}/deadline` | ✅ |
| 11 | POST `/api/v1/projects/close-expired` | 이상함 — 경로/권한은 맞으나 설명이 부정확. "정산 즉시 실행"이 아니라 **성공/실패 판정만** 함(실제 정산은 settlement가 별도 트리거) |
| 12 | POST `/api/v1/projects/{id}/close-early` | ✅ |
| 13 | POST `/api/v1/projects/{id}/rewards` | ✅ (게이트웨이 `hasRole(CREATOR)`) |
| 14 | GET `/api/v1/projects/{id}/rewards` | ✅ (permitAll) |
| 15 | GET `/api/v1/rewards/{id}` | ✅ (permitAll) |
| 16 | PATCH `/api/v1/rewards/{id}` | ✅ |
| 17 | DELETE `/api/v1/rewards/{id}` | 이상함 — "삭제(공개전)/비활성화(공개후)"를 한 엔드포인트가 다 하는 것처럼 읽히지만, 실제론 공개 후엔 **거부(에러)만** 되고 비활성화는 별도 admin 전용 `/deactivate`가 처리 |
| 18 | PATCH `/api/v1/rewards/{id}/quantity` | ✅ |
| 19 | POST `/api/v1/rewards/{id}/deactivate` | ✅ |
| 20 | POST `/api/v1/project-categories` | **이상함** — 컨트롤러가 `requireAdmin()`으로 ADMIN 강제하는데 표엔 인증 없음으로 표기. 게이트웨이도 이 경로에 role 규칙이 없어 ADMIN 여부는 서비스단에서만 걸러짐(위반 시 403 아니라 400) |
| 21 | GET `/api/v1/project-categories` | ✅ (permitAll) |
| 22 | GET `/api/v1/project-categories/{id}` | ✅ (permitAll) |
| 23 | PUT `/api/v1/project-categories/{id}` | **이상함** — 20번과 동일 (ADMIN 강제인데 게이트웨이 차단 없음) |
| 24 | POST `/internal/v1/rewards/{id}/decrease-stock` | ✅ |
| 25 | POST `/internal/v1/rewards/{id}/restore-stock` | ✅ |
| 26 | GET `/internal/v1/projects?status=X` | ✅ |

표에 없던 것: `DELETE /api/v1/project-categories/{id}`(카테고리 삭제, ADMIN 전용, 하위/참조 있으면 거부)가 실제로 존재하는데 원본 표엔 빠져 있었음.

**눈여겨볼 패턴(#7, #20, #23)**: 셋 다 ADMIN(또는 본인) 권한 검증이 게이트웨이가 아니라 project-service 서비스 레이어에서만 이뤄진다 — 다른 admin 전용 액션(approve/reject/close-expired 등)은 게이트웨이 `SecurityConfig`에서부터 이미 막혀있는 것과 대조적. 버그라기보다 게이트웨이 규칙 추가 누락으로 보이며, 이번 라이브 테스트 범위는 아니고 별도 이슈 후보.

## 시나리오별 API 호출 & 기대 결과

배포 환경(게이트웨이 `https://earlybird-team5-api.duckdns.org`)을 대상으로 다음 6개 시나리오를 검증한다. settlement-service와의 상호작용은 범위 밖(위 조사 결과 참고).

**시나리오 1 — 주문 생성 → fundedAmount 증가**

| 순서 | API | 기대 결과 |
| --- | --- | --- |
| 1 | POST `/api/v1/users/signup`, `/login` | 창작자 계정 생성/로그인 성공 |
| 2 | POST `/api/v1/users/me/creator` + `/refresh` | role=CREATOR로 전환 |
| 3 | POST `/api/v1/projects` | status=PENDING_REVIEW |
| 4 | POST `/api/v1/projects/{id}/rewards` | 리워드 생성, remainingQuantity=totalQuantity |
| 5 | POST `/api/v1/projects/{id}/approve` (관리자) | status=IN_PROGRESS |
| 6 | GET `/api/v1/rewards/{id}` (test@test.com 로그인 후) | 리워드 조회 성공 |
| 7 | POST `/api/v1/orders` (userId=4) | order status=PAID, 이 시점 리워드 remainingQuantity -1 |
| 8 | GET `/api/v1/rewards/{id}` (재조회) | remainingQuantity가 -1 된 값으로 확인 |
| 9 | GET `/api/v1/projects/{id}` (최대 60~70초 폴링) | fundedAmount == 리워드 price |

**시나리오 2 — 주문 취소(환불) → fundedAmount 감소**

| 순서 | API | 기대 결과 |
| --- | --- | --- |
| 1~7 | 시나리오 1과 동일하게 주문까지 생성 | fundedAmount가 price만큼 반영된 상태 |
| 8 | POST `/api/v1/orders/{orderId}/cancel` | order status=CANCELLED |
| 9 | GET `/api/v1/rewards/{id}` | remainingQuantity가 다시 +1 (원복) |
| 10 | GET `/api/v1/projects/{id}` (최대 60~70초 폴링) | fundedAmount == 0 (또는 취소 전 값 - price) |

**시나리오 3 — 마감 배치 → 목표 달성(SUCCEEDED)**

| 순서 | API | 기대 결과 |
| --- | --- | --- |
| 1 | POST `/api/v1/projects` (startAt/endAt 둘 다 과거) | status=PENDING_REVIEW |
| 2 | POST `/api/v1/projects/{id}/rewards` (goalAmount == price) | 리워드 생성 |
| 3 | POST `/api/v1/projects/{id}/approve` | status=IN_PROGRESS |
| 4 | POST `/api/v1/orders` (test@test.com) → 목표금액 달성 | order PAID |
| 5 | GET `/api/v1/projects/{id}` (폴링) | fundedAmount == goalAmount 확인 |
| 6 | POST `/api/v1/projects/close-expired` (관리자) | 200 반환 |
| 7 | GET `/api/v1/projects/{id}` | status==SUCCEEDED |

**시나리오 4 — 마감 배치 → 목표 미달(FAILED)**

| 순서 | API | 기대 결과 |
| --- | --- | --- |
| 1~3 | 시나리오 3과 동일 (단, 주문 없음 → fundedAmount=0) | status=IN_PROGRESS |
| 4 | POST `/api/v1/projects/close-expired` | 200 반환 |
| 5 | GET `/api/v1/projects/{id}` | status==FAILED |

**시나리오 5 — 조기종료(SUCCEEDED)**

| 순서 | API | 기대 결과 |
| --- | --- | --- |
| 1~5 | 시나리오 3의 1~5와 동일 (단, endAt은 미래) | fundedAmount==goalAmount, status는 아직 IN_PROGRESS |
| 6 | POST `/api/v1/projects/{id}/close-early` (관리자) | 응답 즉시 status==SUCCEEDED |

**시나리오 6 — 취소(CANCELLED)**

| 순서 | API | 기대 결과 |
| --- | --- | --- |
| 1~3 | 프로젝트 생성→승인 (IN_PROGRESS) | status=IN_PROGRESS |
| 4 | POST `/api/v1/projects/{id}/cancel` (창작자 본인 또는 관리자) | 응답 즉시 status==CANCELLED |

## 아키텍처

기존 `system-test` 모듈에 새 파일 `ProjectLifecycleLiveTest.java` 하나를 추가한다. `CreatorFlowLiveTest`/`BackerFlowLiveTest`와 동일한 패턴(`@Tag("live")`, `HttpClient` 직접 사용, `post()`/`get()` private 헬퍼를 파일 안에 그대로 중복 — 기존 두 파일도 공유 베이스 클래스 없이 이렇게 되어 있어 그 컨벤션을 따른다)을 쓰되, 6개 시나리오는 서로 다른 상태의 프로젝트가 필요하므로 페르소나 흐름(회원가입→...→승인 한 줄로 이어지는 체인)이 아니라 **시나리오별로 독립적인 `@Test` 메서드**로 구성한다. 각 메서드가 필요한 프로젝트를 스스로 만들고 검증까지 마친다.

로그인 계정:
- 창작자 계정: 매 시나리오마다 타임스탬프로 새로 가입(기존 두 파일과 동일한 패턴, `projectlifecycle.<timestamp>@earlybird.co.kr`)
- 후원자 계정: 이미 존재하는 시드 계정 `test@test.com` / `1234` 재사용 (신규 가입 불필요). 로그인 응답 확인 결과 `userId = 4`, `role = BACKER` — `BackerFlowLiveTest.placeOrder()`의 요청 바디에 필요한 `userId` 필드에 이 값을 그대로 쓴다(매번 로그인해서 JWT는 새로 받고, `userId`만 하드코딩 상수로 둔다).
- 관리자 계정: 기존 두 파일과 동일하게 시드 계정 `admin@earlybird.co.kr` 재사용

## 컴포넌트 변경

`system-test/src/test/java/com/growmighty/lectures/firstday/systemtest/ProjectLifecycleLiveTest.java` (신규)

- 폴링 헬퍼: `waitUntil(Supplier<Boolean> condition, Duration timeout, Duration pollInterval)` — fundedAmount 반영 확인용. 조건이 timeout 안에 참이 안 되면 `AssertionError`로 실패 처리.
- 목표금액(`goalAmount`)을 리워드 가격과 정확히 맞춰(예: `goalAmount = price`, `quantity = 1`) 주문 1건으로 목표 달성이 정확히 갈리도록 설계 — 여러 건 주문할 필요 없이 시나리오를 단순하게 유지.
- `endAt`을 과거로 만드는 시나리오(3, 4번)는 프로젝트 생성 요청의 `startAt`/`endAt`을 둘 다 과거로 지정(예: `startAt = 어제 -N일`, `endAt = 어제`) — `validatePeriod`는 "시작일 이후"만 보므로 통과.
- 관리자 승인(`approve`) 후 상태가 실제로 `IN_PROGRESS`인지 먼저 확인한 뒤 각 시나리오의 본 검증으로 진행 (기존 `CreatorFlowLiveTest.adminApprovesProject()`와 동일한 어서션 스타일).

## 확인이 필요한 가정 (테스트 작성 중 1차 검증)

- fundedAmount가 정확히 "리워드 가격 × 수량"인지, 배송비(`shippingFee`)는 포함되지 않는지 — `BackerFlowLiveTest.placeOrder()`의 `expectedItemsAmount`/`expectedTotalAmount` 계산 로직을 참고해 fundedAmount 기대값을 `itemsAmount` 기준으로 잡는다.
- 이미 지난 `endAt`으로 프로젝트 생성이 실제 배포 환경에서도 막혀있지 않은지 (코드 근거는 확인했으나 최종 확인은 테스트 1차 실행에서).
- `close-expired`가 다른 팀원이 동시에 만든 테스트/데모 프로젝트까지 함께 마감시킬 수 있음 — 이번 테스트가 만든 프로젝트만 확인 대상으로 삼고(응답에서 해당 projectId로 필터링), 부수효과(다른 프로젝트가 같이 마감됨) 자체는 문제 삼지 않는다.

## 에러 처리

- HTTP 호출 실패(4xx/5xx)는 기존 두 파일의 `post()`/`get()` 헬퍼처럼 `assertThat(statusCode).isEqualTo(expected)`로 즉시 실패 처리 — 별도 재시도 없음.
- fundedAmount 폴링이 타임아웃(60~70초) 안에 조건을 못 만족하면 테스트 실패 — 이 자체가 "1분 이내 반영"이라는 요구사항 위반을 잡아내는 것이므로 정상 동작.

## 트레이드오프

- 실제 AWS 배포 환경에 진짜 유저/프로젝트/주문 데이터가 생성된다. 이메일 타임스탬프로 구분은 되지만 별도 cleanup(삭제)은 하지 않는다 — 데모/발표 데이터와 뒤섞일 가능성이 있으나, 기존 `CreatorFlowLiveTest`/`BackerFlowLiveTest`도 동일한 방식이라 새로운 리스크는 아니다.
- 6개 시나리오 각각 프로젝트를 새로 만들다 보니 테스트 실행 시간이 길다(특히 fundedAmount 폴링 2회 × 최대 70초) — CI 기본 `test` 태스크에서는 여전히 제외(`@Tag("live")`)되므로 평소 빌드 속도에는 영향 없음.

## 테스트

이 작업 자체가 시스템(라이브) 테스트 추가이므로 별도의 단위 테스트는 없다. 검증은 `./gradlew :system-test:liveTest -Dsystem-test.baseUrl=https://earlybird-team5-api.duckdns.org` 실행 결과로 한다.

## 범위 밖

- settlement-service와의 상호작용 검증 (팀 결정으로 자동 트리거 자체가 없음 — 위 경위 참고)
- 부하/동시성 테스트(재고 100개/동시 요청 1,000건 시나리오) — 별도 작업으로 갭 리포트에 기록됨
- 생성된 테스트 데이터의 정리(cleanup) 자동화
- 게이트웨이 ADMIN 권한 검증 누락(#7, #20, #23) 수정 — 이번 작업 범위 밖, 별도 이슈 후보
