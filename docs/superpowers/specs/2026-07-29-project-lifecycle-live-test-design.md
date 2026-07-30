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

라이브 테스트가 호출할 API들을 실제 코드(project-service 컨트롤러 + gateway `SecurityConfig`, `origin/main` 기준 = 배포 환경과 동일)와 대조한 결과. **가능한 권한**은 게이트웨이 `pathMatchers` 규칙(없으면 `anyExchange().authenticated()`로 폴백 — 로그인만 하면 역할 무관 통과) 기준이고, 서비스 레이어에서 추가로 본인 확인을 하는 경우 별도 표기했다. 2026-07-30 재검증 시점엔 #7/#20/#23이 PR #175(김하나한, `gateway/project-categories-delete-role`)로 이미 게이트웨이에 반영·배포돼 있었다 — 아래 표는 그 상태 기준이며, 실제로 403/200이 기대대로 나오는지는 이번 라이브 실행에서 확인했다(결과는 각 항목에 표기).

| # | API | 가능한 권한 | API 정의 | 검증 |
|---|---|---|---|---|
| 1 | POST `/api/v1/projects` | CREATOR, ADMIN | 프로젝트 등록. body: `title`/`categoryId`/`goalAmount`/`startAt`/`endAt`(필수), `thumbnailId`/`summary`/`description`(선택) → PENDING_REVIEW로 생성 | ✅ 라이브 실행: 200, PENDING_REVIEW 확인(projectId 39) |
| 2 | GET `/api/v1/projects` | permitAll | 목록 조회. `keyword`/`categoryId`/`status`/`sort` 쿼리. 비로그인이면 컨트롤러가 BACKER로 간주(공개된 것만), ADMIN이면 심사대기/반려도 포함 | 이상함 — 게이트웨이는 `permitAll`인데 "X-User-Role 필요"처럼 표기됐던 과거 문서 오류. 헤더 없으면 컨트롤러가 BACKER로 간주 |
| 3 | GET `/api/v1/projects/me` | CREATOR | 내 프로젝트 목록 (X-User-Id 기준) | ✅ 라이브 실행: 200 |
| 4 | GET `/api/v1/projects/{id}` | permitAll | 단건 조회 | ✅ (permitAll) |
| 5 | PATCH `/api/v1/projects/{id}` | CREATOR(게이트웨이) + 본인확인(서비스) | 수정. 공개 전: 전 필드. 공개 후: `summary`/`description`/`thumbnailId`만 | ✅ (게이트웨이 `hasRole(CREATOR)`, 본인 확인은 서비스 레이어) |
| 6 | DELETE `/api/v1/projects/{id}` | CREATOR(게이트웨이) + 본인확인(서비스) | 삭제 | ✅ |
| 7 | POST `/api/v1/projects/{id}/cancel` | CREATOR, ADMIN(게이트웨이) + 본인확인(서비스) | 진행중/성공 프로젝트 자진 취소 → CANCELLED | ✅ **라이브 실행으로 확인**: BACKER 토큰 → 403, 창작자 본인 → 200/CANCELLED (project 44) |
| 8 | POST `/api/v1/projects/{id}/approve` | ADMIN | 심사 승인 PENDING_REVIEW→IN_PROGRESS | ✅ 라이브 실행: 200, IN_PROGRESS 확인 (2회) |
| 9 | POST `/api/v1/projects/{id}/reject` | ADMIN | 심사 반려 PENDING_REVIEW→REJECTED, body: `reason` | ✅ (미실행, 코드 검증만) |
| 10 | PATCH `/api/v1/projects/{id}/deadline` | ADMIN | 마감일 연장(기존보다 뒤로만), body: `endAt` | ✅ (미실행, 코드 검증만) |
| 11 | POST `/api/v1/projects/close-expired` | ADMIN | 마감 지난 IN_PROGRESS 프로젝트 일괄 성공/실패 확정 즉시 실행 | ✅ 라이브 실행: 200, FAILED 판정 확인 (project 45) |
| 12 | POST `/api/v1/projects/{id}/close-early` | ADMIN | 목표 달성한 프로젝트 조기 종료(성공 확정) | ⚠️ 미실행 — fundedAmount==goalAmount를 만들려면 주문 성공이 선행돼야 하는데 order-service `POST /api/v1/orders` 500 버그(아래 "실행 결과" 참고)에 막혀서 이번엔 못 돌림 |
| 13 | POST `/api/v1/projects/{id}/rewards` | CREATOR | 리워드 등록, body: `name`/`price` 필수, `totalQuantity` 비우면 무제한 | ✅ 라이브 실행: 200, remainingQuantity=totalQuantity 확인 |
| 14 | GET `/api/v1/projects/{id}/rewards` | permitAll | 프로젝트의 리워드 목록 | ✅ (permitAll) |
| 15 | GET `/api/v1/rewards/{id}` | permitAll | 리워드 단건 조회 | ✅ 라이브 실행: 200 |
| 16 | PATCH `/api/v1/rewards/{id}` | CREATOR(게이트웨이) + 본인확인(서비스) | 공개 전 자유수정, 공개 후 `increaseQuantity`만 | ✅ (미실행, 코드 검증만) |
| 17 | DELETE `/api/v1/rewards/{id}` | CREATOR(게이트웨이) + 본인확인(서비스) | 공개 전에만 하드 삭제 가능, 공개 후 거부(→ 관리자가 `/deactivate` 사용) | ✅ (미실행, 코드 검증만) |
| 18 | PATCH `/api/v1/rewards/{id}/quantity` | ADMIN | 공개 리워드 수량 축소(부득이한 경우만), body: `amount` | ✅ (미실행, 코드 검증만) |
| 19 | POST `/api/v1/rewards/{id}/deactivate` | ADMIN | 공개 리워드 비활성화(레코드 보존) | ✅ (미실행, 코드 검증만) |
| 20 | POST `/api/v1/project-categories` | ADMIN | 카테고리 생성(전역 taxonomy), body: `parentProjectCategoryId`/`name` | ✅ **라이브 실행으로 확인**: CREATOR 토큰 → 403, ADMIN → 200 |
| 21 | GET `/api/v1/project-categories` | permitAll | 카테고리 트리 조회 | ✅ 라이브 실행: 200 (단, #카테고리중복 버그 발견 — 아래 참고) |
| 22 | GET `/api/v1/project-categories/{id}` | permitAll | 카테고리 단건 조회 | ✅ (permitAll) |
| 23 | PUT `/api/v1/project-categories/{id}` | ADMIN | 카테고리 이름/부모 변경 | ✅ **라이브 실행으로 확인**: CREATOR 토큰 → 403, ADMIN → 200 |
| 24 | DELETE `/api/v1/project-categories/{id}` | ADMIN | 카테고리 삭제, 하위/참조 프로젝트 있으면 거부 | ✅ 라이브 실행: 200 (테스트로 만든 카테고리 정리용으로 사용, 정상 삭제됨) — 원본 표엔 이 행 자체가 빠져 있었음 |
| 25 | GET `/internal/v1/projects/{id}/creator` | 내부망 전용(게이트웨이 라우트 없음) | board-service 리뷰알림용 제작자 조회 | ✅ (코드 검증만, 원본 표에 없던 항목) |
| 26 | POST `/internal/v1/rewards/{id}/decrease-stock` | 내부망 전용 | order-service가 재고 확보 시 호출 | ✅ 간접 확인 — 라이브 주문 생성 시 실제로 재고가 감소함(remainingQuantity 5→3) |
| 27 | POST `/internal/v1/rewards/{id}/restore-stock` | 내부망 전용 | order-service가 재고 복원 시 호출 | ⚠️ 미확인 — 주문 생성 500 경로에서 이 호출까지 도달하는지가 이번에 드러난 버그의 핵심 의문점 (아래 "실행 결과" 참고) |
| 28 | GET `/internal/v1/projects?status=X` | 내부망 전용 | settlement-service가 정산/환불 대상 조회 | ✅ (미실행, 코드 검증만) |

**#7·#20·#23 관련 정정**: 문서 초안 시점엔 이 셋의 게이트웨이 규칙이 없어서 "이상함"으로 표시했는데, 이후 PR #175로 `develop`·`main` 양쪽에 이미 반영·배포됐다 (`강대혁/project/lifecycle-live-test` 로컬 브랜치만 그 커밋 이전 상태라 처음엔 없는 것처럼 보였음 — `git fetch` 후 재확인해서 발견). 이번 라이브 실행에서 셋 다 기대대로(비권한 403 / 권한 200) 동작하는 것까지 확인했다.

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

## 실행 결과 (2026-07-30, 배포 환경에 실제 curl로 재실행)

`ProjectLifecycleLiveTest.java` 자동화 코드를 아직 작성하지 않은 상태라, 이번 회차는 설계 문서의 각 시나리오를 실제 배포 게이트웨이(`https://earlybird-team5-api.duckdns.org`)에 curl로 그대로 실행해 결과를 확인했다(코드 작성 전 마지막 수동 리허설 성격). 사용한 창작자 계정: `livetest-creator-*@earlybird.co.kr`(userId=15), 후원자 계정: `livetest-backer-*@earlybird.co.kr`(userId=16, 아래 이유로 새로 만듦), 관리자: 시드 계정 `admin@earlybird.co.kr`.

**시나리오 1 — 부분 통과, 8번(주문 생성)에서 막힘**

| 단계 | 실행 | 결과 |
| --- | --- | --- |
| 1~5 | 회원가입→로그인→창작자 전환→refresh→카테고리 조회 | ✅ 전부 200, role=CREATOR 토큰 확보 |
| 6 | 프로젝트 생성(goalAmount=10000, endAt=+30일) | ✅ 200, projectId=39, PENDING_REVIEW |
| 7 | 리워드 등록(price=10000, totalQuantity=5) | ✅ 200, rewardId=84, remainingQuantity=5 |
| — | 관리자 승인 | ✅ 200, IN_PROGRESS |
| 8 | 후원자 로그인 — 문서에 적힌 시드 계정 `test@test.com`/`1234` | ❌ **401 "이메일 또는 비밀번호가 올바르지 않습니다"** — 문서의 가정이 틀렸다. 실제 비밀번호를 모름. 아래 "로그인 계정" 절 정정 참고 |
| 8(대체) | 새 후원자 계정 회원가입으로 우회 | ✅ 200, userId=16 |
| 9 | POST `/api/v1/orders` — `{"userId":16,"requests":[{"rewardId":84,"quantity":1,"expectedUnitPrice":10000}],"expectedItemsAmount":10000,"expectedTotalAmount":13000, ...}` | ❌ **HTTP 500 "서버 오류가 발생했습니다"**. 재시도해도 동일(일시적 아님) |

**막힌 지점 상세**: 500이지만 부작용은 실제로 일어난다 — 재시도 2회 후 리워드 `remainingQuantity`가 5→3으로 정확히 감소했고(`rewardPort.decreaseStock` 호출 자체는 성공), 후원자의 `/api/v1/orders/me` 조회에 주문 2건이 잡히지만 **둘 다 status=CREATED에 멈춰 있다**(PAYMENT_REQUEST/PAID로 못 넘어감). `OrderApiService.placeOrder()`를 코드로 보면 `@Transactional`이 없이 `saveAndFlush` → `reserveStock()` → `markPaymentRequested()+save` → `paymentPort.pay()`(스텁, 항상 성공) → `markPaid()+save` 순으로 여러 번 개별 커밋되는 구조라, `reserveStock()` 성공 이후 어딘가에서 예외가 나면 **재고 차감은 롤백되지 않고 주문만 CREATED로 방치**되는 것으로 보인다(코드 주석에도 "Project service must provide a multi-reward atomic reservation endpoint" TODO가 이미 있음). 서버 로그에 직접 접근하지 못해 정확한 예외 종류(어느 save가 실패하는지, `PaymentHttpClient` 스텁 자체는 네트워크 호출이 없어 그쪽은 아닐 가능성이 높음)는 확정하지 못했다 — **다음 실행 전 order-service 로그 확인이 필요**.

**재현성 확인(같은 날 서버 안정화 이후 재시도)**: 이 문서의 "배포 서버 일시 다운" 사건이 원인이었을 가능성을 배제하기 위해, 서버가 완전히 정상(카테고리 조회 200)으로 복귀한 뒤 **완전히 새로운 프로젝트(46)/리워드(94)/후원자 계정(userId=18)으로 처음부터 재시도**했다. 결과는 동일 — 재고 5→4로 감소, order(id=6) 생성되지만 status=CREATED에서 멈추고 500 반환. **인프라 불안정과 무관하게 항상 재현되는 결정적(deterministic) 코드 버그로 확인됨.**

**시나리오 2, 3, 5 — 미실행(시나리오 1의 500에 의존)**: 주문이 PAID로 성공해야 진행 가능한 시나리오라 이번엔 돌리지 못했다.

**시나리오 4 — 통과**: 과거 startAt/endAt(startAt=-10일, endAt=-1일)으로 프로젝트 생성(projectId=45) → 승인(IN_PROGRESS) → `close-expired` 호출(200) → 재조회 시 **status=FAILED, closed=true** 확인. `validatePeriod()`가 "오늘 이후"를 검증하지 않는다는 코드 근거가 실제 배포 환경에서도 그대로 맞았다.

**시나리오 6 — 통과**: 프로젝트 생성(projectId=44) → 승인(IN_PROGRESS) → BACKER 토큰으로 cancel 시도 시 **403**(게이트웨이 `hasAnyRole(CREATOR,ADMIN)` 정상 작동) → 창작자 본인 토큰으로 cancel 시 **200, status=CANCELLED, closed=true** 즉시 확인.

**권한 체크(#7/#20/#23) — 셋 다 라이브로 확인 완료**: 위 API 검증 결과 표에 반영.

**부수적으로 발견한 것 (이번 시나리오 자체와는 별개)**

1. **로그인 계정 가정 오류**: "로그인 계정" 절의 `test@test.com`/`1234`는 실제 배포 환경 비밀번호가 아니었다(401). 이 문서 작성 시점에 확인 안 하고 가정만으로 적었던 것으로 보임 — 아래 절 정정.
2. **project-service 카테고리 시드 데이터 중복 생성 의심**: 테스트 중간에 `GET /api/v1/project-categories`를 재조회했더니 패션/의류/전자기기 등 같은 카테고리 세트가 **id 1~14, 15~28, 29~42 ... 식으로 10세트 넘게 중복**돼 있었다(테스트 시작 시점엔 1세트, 14개였음). project-service에도 `UserDataInitializer`류의 카테고리 시더가 있고 재시작마다 "이미 있으면 skip" 가드 없이 다시 INSERT하는 것으로 추정된다 — 이번 세션 중 서버가 몇 차례 재시작된 것과 시점이 겹친다. 별도 확인/이슈화 필요.
3. **배포 서버 일시 다운(502→503, 자연 복구)**: 시나리오 1 테스트 중간에 게이트웨이 뒤 서비스들(project-service, user-service 최소 둘 다 확인)이 약 5~10분간 502/503을 반환했다. Caddy·gateway-server 자체는 살아있었고(루트 경로 401 정상), 백엔드 서비스들만 Eureka 로드밸런서 입장에서 인스턴스를 못 찾는 상태였던 것으로 보임 — discovery-server 또는 EC2 리소스 문제로 추정되나 확정은 못 했다. 아무 개입 없이 자연 복구됐다. `cd.yml` 주석에 "2-vCPU 박스라 동시 빌드 시 sshd까지 응답 안 해서 리붓해야 했던 전례가 있다"는 기록이 있어, 이 배포 환경이 원래도 리소스 여유가 빠듯한 상태였을 가능성이 있다. 원인 확정은 EC2/서비스 로그 직접 확인이 필요.

## 아키텍처

기존 `system-test` 모듈에 새 파일 `ProjectLifecycleLiveTest.java` 하나를 추가한다. `CreatorFlowLiveTest`/`BackerFlowLiveTest`와 동일한 패턴(`@Tag("live")`, `HttpClient` 직접 사용, `post()`/`get()` private 헬퍼를 파일 안에 그대로 중복 — 기존 두 파일도 공유 베이스 클래스 없이 이렇게 되어 있어 그 컨벤션을 따른다)을 쓰되, 6개 시나리오는 서로 다른 상태의 프로젝트가 필요하므로 페르소나 흐름(회원가입→...→승인 한 줄로 이어지는 체인)이 아니라 **시나리오별로 독립적인 `@Test` 메서드**로 구성한다. 각 메서드가 필요한 프로젝트를 스스로 만들고 검증까지 마친다.

로그인 계정:
- 창작자 계정: 매 시나리오마다 타임스탬프로 새로 가입(기존 두 파일과 동일한 패턴, `projectlifecycle.<timestamp>@earlybird.co.kr`)
- 후원자 계정: ~~이미 존재하는 시드 계정 `test@test.com` / `1234` 재사용~~ — **2026-07-30 라이브 실행에서 틀린 것으로 확인됨(401)**. `test@test.com`(userId=4) 계정 자체는 존재하지만 비밀번호가 `1234`가 아니다. 대신 창작자와 동일하게 **매 시나리오마다 새로 회원가입**하는 방식으로 변경한다(`projectlifecycle-backer.<timestamp>@earlybird.co.kr`) — 로그인 응답의 `user.id`를 그대로 `userId`로 쓰면 되므로 하드코딩 상수가 필요 없어져서 오히려 더 단순해진다.
- 관리자 계정: 기존 두 파일과 동일하게 시드 계정 `admin@earlybird.co.kr` 재사용 (비밀번호 `rawPassword3!` — 이번 라이브 실행에서 재확인함)

## 컴포넌트 변경

`system-test/src/test/java/com/growmighty/lectures/firstday/systemtest/ProjectLifecycleLiveTest.java` (신규)

- 폴링 헬퍼: `waitUntil(Supplier<Boolean> condition, Duration timeout, Duration pollInterval)` — fundedAmount 반영 확인용. 조건이 timeout 안에 참이 안 되면 `AssertionError`로 실패 처리.
- 목표금액(`goalAmount`)을 리워드 가격과 정확히 맞춰(예: `goalAmount = price`, `quantity = 1`) 주문 1건으로 목표 달성이 정확히 갈리도록 설계 — 여러 건 주문할 필요 없이 시나리오를 단순하게 유지.
- `endAt`을 과거로 만드는 시나리오(3, 4번)는 프로젝트 생성 요청의 `startAt`/`endAt`을 둘 다 과거로 지정(예: `startAt = 어제 -N일`, `endAt = 어제`) — `validatePeriod`는 "시작일 이후"만 보므로 통과.
- 관리자 승인(`approve`) 후 상태가 실제로 `IN_PROGRESS`인지 먼저 확인한 뒤 각 시나리오의 본 검증으로 진행 (기존 `CreatorFlowLiveTest.adminApprovesProject()`와 동일한 어서션 스타일).

## 확인이 필요한 가정 (테스트 작성 중 1차 검증)

- ~~이미 지난 `endAt`으로 프로젝트 생성이 실제 배포 환경에서도 막혀있지 않은지~~ — **2026-07-30 라이브 실행으로 확인 완료.** 실제로 생성되고, `close-expired` 호출 시 FAILED로 정상 판정됨(위 "실행 결과" 참고).
- `close-expired`가 다른 팀원이 동시에 만든 테스트/데모 프로젝트까지 함께 마감시킬 수 있음 — 이번 테스트가 만든 프로젝트만 확인 대상으로 삼고(응답에서 해당 projectId로 필터링), 부수효과(다른 프로젝트가 같이 마감됨) 자체는 문제 삼지 않는다.
- **신규**: `POST /api/v1/orders`가 재고 차감 이후 500을 반환하며 주문이 CREATED에 방치되는 버그가 발견됐다(위 "실행 결과" 시나리오 1 참고) — 이 API에 의존하는 시나리오 1/2/3/5는 이 버그가 고쳐지기 전엔 `ProjectLifecycleLiveTest` 코드를 짜도 항상 실패한다. **코드 작성에 앞서 order-service 쪽에서 이 버그를 먼저 고쳐야 하는지, 아니면 테스트를 "500이 나면 스킵/알려진 실패로 표시"하도록 짤지 팀 결정이 필요.**

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
- 생성된 테스트 데이터의 정리(cleanup) 자동화 (단, 이번에 만든 테스트 카테고리 1건은 정리용 DELETE API 자체를 검증할 겸 수동으로 삭제했다)
- ~~게이트웨이 ADMIN 권한 검증 누락(#7, #20, #23) 수정~~ — PR #175로 이미 반영·배포됨, 이번 라이브 실행으로 동작까지 확인 완료(위 "실행 결과" 참고)
- **신규**: `POST /api/v1/orders` 500 버그 자체의 수정 — order-service 담당(이 문서는 project-service 담당 범위), 이번엔 발견·기록만 한다
- **신규**: project-service 카테고리 시드 데이터 중복 생성 의심 건의 원인 확인 — 이번엔 증상만 기록, 별도 조사 필요
