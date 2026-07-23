# project-service 파일 가이드 — Category / Project / Reward

`DOMAIN_GUIDE_Category_Project_Reward.md`가 "왜 이런 규칙이 있는가"(비즈니스 로직)를 설명하는 문서라면, 이 문서는 **"실제 파일이 몇 개고 각자 뭘 하는가"**를 지도처럼 훑어보는 문서입니다. 새로 이 코드를 보는 사람이 "이 기능을 고치려면 어느 파일을 열어야 하지?"를 빨리 찾을 수 있게 하는 게 목적입니다.

---

## 0. 세 도메인 모두 똑같은 4층 구조

```
category/  (또는 project/, reward/)
├── presentation/    ← 컨트롤러 + 요청/응답 DTO
├── application/     ← 서비스 인터페이스 + 구현체
├── domain/          ← 엔티티 (진짜 규칙이 사는 곳)
└── infrastructure/  ← JPA 리포지토리, 외부 서비스 호출 어댑터
```

**왜 매번 이 4개로 나누는가?** 계층마다 "무엇을 몰라도 되는지"가 다르기 때문입니다.

- `domain`은 DB가 뭔지, HTTP가 뭔지 몰라도 됩니다 — 순수 자바로 업무 규칙만 압니다.
- `application`은 "이 순서로 처리해라"만 알지, SQL을 직접 안 씁니다.
- `infrastructure`는 "어떻게 저장/호출하는지"만 알지, 왜 그렇게 해야 하는지(업무 규칙)는 모릅니다.
- `presentation`은 "HTTP로 뭘 받았는지"만 알지, 그걸로 뭘 해야 하는지는 `application`에 위임합니다.

이렇게 나누면 예를 들어 "DB를 MySQL에서 다른 걸로 바꾼다"고 해도 `infrastructure`만 고치면 되고, `domain`(진짜 규칙)은 한 줄도 안 건드립니다.

---

## 1. Category 도메인

```
category/
├── domain/
│   └── ProjectCategory.java
├── infrastructure/
│   └── ProjectCategoryRepository.java
├── application/
│   ├── ProjectCategoryService.java       (인터페이스)
│   └── ProjectCategoryServiceImpl.java   (구현체)
└── presentation/
    ├── ProjectCategoryController.java
    └── dto/
        ├── request/ProjectCategoryCreateRequest.java
        ├── request/ProjectCategoryUpdateRequest.java
        └── response/ProjectCategoryResponse.java
```

가장 단순한 도메인이라 구조 익히기 좋은 출발점입니다.

| 파일 | 역할 |
| --- | --- |
| `ProjectCategory.java` | 엔티티. 자기 부모 카테고리의 **id 숫자**(`parentProjectCategoryId`)만 들고 있음 — JPA `@ManyToOne` 연관관계가 아니라 그냥 `Long` 컬럼. `rename()`/`changeParent()`가 유일한 상태 변경 통로. |
| `ProjectCategoryRepository.java` | `JpaRepository<ProjectCategory, Long>` 상속 인터페이스 한 줄. 포트/어댑터 분리 없이 Spring Data JPA가 구현체를 자동 생성. |
| `ProjectCategoryService.java` / `Impl.java` | `create`/`findAllAsTree`/`findById`/`update`/`delete`. `findAllAsTree()`가 이 도메인의 핵심 로직 — flat하게 저장된 목록을 부모-자식으로 조립. `update()`는 순환 참조(자기 자손을 부모로 지정) 방지 검증을 포함. |
| `ProjectCategoryController.java` | `/api/v1/project-categories` 아래 4개 엔드포인트(생성/트리조회/단건조회/수정). DELETE는 아직 없음(정책 미정). |
| `dto/request/*.java` | `Create`는 `toEntity()`로 자기 자신을 엔티티로 변환하는 팩토리 메서드를 가짐. `Update`는 변환 없이 필드만 담는 순수 그릇. |
| `dto/response/ProjectCategoryResponse.java` | `children: List<ProjectCategoryResponse>`를 가진 재귀적 구조 — 트리 모양 응답의 핵심. `of(entity, children)`과 `leaf(entity)`(children을 빈 리스트로 채우는 지름길) 두 가지 정적 팩토리가 있음. |

**어려운 부분 예시 — 트리 조립이 실제로 어떻게 일어나는가**

DB엔 이렇게 flat하게 저장돼 있습니다:

```
id=1, parentId=null, name="전자기기"
id=2, parentId=1,    name="스마트기기"
id=3, parentId=2,    name="이어폰"
```

`findAllAsTree()`는 이걸 이렇게 조립합니다:

1. 전체를 한 번에 `findAll()`로 가져온다.
2. `parentProjectCategoryId` 기준으로 그룹핑한다 → `{1: [카테고리2], 2: [카테고리3]}`
3. `parentId == null`인 것(루트, 여기선 id=1)부터 시작해서, 이 맵을 보며 재귀적으로 자식을 채운다.

결과: `{id:1, children:[{id:2, children:[{id:3, children:[]}]}]}` — 딱 한 번의 쿼리로 몇 단계든 깊이의 트리를 완성합니다. (반대로 `ProjectCategoryController.findById()`는 이 조립을 안 하고 `leaf()`로 항상 `children: []`만 반환합니다 — 트리가 필요하면 무조건 목록 조회를 써야 합니다.)

---

## 2. Project 도메인

```
project/
├── domain/
│   ├── Project.java
│   ├── ProjectStatus.java
│   └── ProjectSort.java
├── infrastructure/
│   ├── ProjectRepository.java
│   └── client/
│       ├── OrderFeignClient.java
│       ├── OrderHttpClient.java
│       └── OrderCircuitBreakerConfig.java
├── application/
│   ├── ProjectService.java            (인터페이스)
│   ├── ProjectServiceImpl.java        (구현체)
│   ├── ProjectStatusView.java         (다른 도메인에게 보여줄 값 객체)
│   ├── ProjectDeadlineScheduler.java
│   └── port/OrderPort.java
└── presentation/
    ├── ProjectController.java
    ├── ProjectInternalController.java
    └── dto/
        ├── request/ProjectCreateRequest.java
        ├── request/ProjectUpdateRequest.java
        ├── request/ProjectRejectRequest.java
        ├── request/ProjectDeadlineExtendRequest.java
        └── response/ProjectResponse.java
```

세 도메인 중 가장 파일이 많은데, 이유는 **(a) 상태 전이가 복잡하고 (b) 다른 서비스(order-service)를 직접 호출하는 유일한 도메인**이기 때문입니다.

### 2.1 domain/ — 진짜 규칙

| 파일 | 역할 |
| --- | --- |
| `Project.java` | 엔티티이자 이 도메인의 심장. `register()`/`approve()`/`reject()`/`updateBeforePublish()`/`updateAfterPublish()`/`extendDeadline()`/`closeByDeadline()`/`closeEarlyAsSucceeded()`/`cancel()` — 상태를 바꾸는 통로가 이 메서드들뿐이고, 각자 "지금 이 상태여야만 호출 가능"을 검증해서 엉뚱한 순서로 상태가 바뀌는 걸 막는다. `@Version`(낙관적 락)도 여기 있음 — 마감 배치와 관리자의 마감일 연장이 동시에 같은 행을 건드릴 수 있어서. |
| `ProjectStatus.java` | `PENDING_REVIEW`/`REJECTED`/`IN_PROGRESS`/`SUCCEEDED`/`FAILED`/`CANCELLED` 6개 값 + `isClosed()`(성공/실패/취소면 true). |
| `ProjectSort.java` | 목록 정렬 옵션(`LATEST`/`DEADLINE`/`FUNDED_AMOUNT`) → Spring Data `Sort` 객체로 변환하는 얇은 enum. |

### 2.2 application/ — 흐름 조율

| 파일 | 역할 |
| --- | --- |
| `ProjectService.java` | 인터페이스. 일반 CRUD + 관리자 전용(승인/반려/마감연장/조기종료/마감배치) + **Reward가 호출하는 `findStatusView()`**까지 한 인터페이스에 다 있음. |
| `ProjectServiceImpl.java` | 실제 구현. 소유권 검증(`validateOwnership`), 카테고리 존재 검증, 동적 검색 조립(`buildSpecification`) 등 이 도메인의 모든 조율 로직. |
| `ProjectStatusView.java` | Reward 도메인에게 프로젝트 상태를 알려줄 때 쓰는 **값 객체**(record). `Project` 엔티티 전체가 아니라 `published`/`closed`/`open`/`status`/`creatorId` 5개 필드만 담음 — 아래 "어려운 부분" 참고. |
| `ProjectDeadlineScheduler.java` | `@Scheduled(cron = "0 0 0 * * *")`로 매일 자정(Asia/Seoul) `closeExpiredProjects()`를 호출하는 진입점. 로직은 하나도 없고 트리거만 함. |
| `port/OrderPort.java` | "order-service에게 뭘 물어볼 건지"의 계약. 지금은 `hasOrderedReward(projectId)` 하나뿐. |

### 2.3 infrastructure/ — 저장 + 외부 호출

| 파일 | 역할 |
| --- | --- |
| `ProjectRepository.java` | `JpaRepository` + `JpaSpecificationExecutor`(동적 검색용) 상속. `findByStatusAndEndAtLessThan`(마감 배치용), `findByIdForStatusCheck`(공유 락 조회, Reward가 상태 확인할 때 씀) 같은 커스텀 쿼리 메서드 보유. |
| `client/OrderFeignClient.java` | order-service를 향한 **선언적** HTTP 인터페이스. `@FeignClient(name="order-service")` — 실제 URL이 아니라 Eureka에 등록된 서비스 이름으로 호출. |
| `client/OrderHttpClient.java` | `OrderPort`의 진짜 구현체(어댑터). `OrderFeignClient` 호출을 서킷브레이커로 감싸고, 실패하면 무작정 통과시키지 않고 503으로 fail-closed. |
| `client/OrderCircuitBreakerConfig.java` | 서킷브레이커 세부 설정(타임아웃 3초, 실패율 50% 이상이면 차단 등) — Spring Bean으로 등록. |

### 2.4 presentation/ — HTTP 경계

| 파일 | 역할 |
| --- | --- |
| `ProjectController.java` | `/api/v1/projects` 아래 일반 CRUD + 취소 + 관리자 전용(승인/반려/마감연장/마감배치트리거/조기종료) 엔드포인트를 **한 컨트롤러에** 다 모음(URL에 role 노출 안 하려고 일부러 안 나눔). |
| `ProjectInternalController.java` | `/internal/v1/projects` — Settlement가 정산/환불 대상 프로젝트를 상태별로 조회할 때 쓰는, 게이트웨이를 안 거치는 서비스 간 전용 API. 딱 `findByStatus()` 하나만 노출. |
| `dto/request/*.java` | 각 엔드포인트의 요청 바디. `ProjectCreateRequest.toEntity(creatorId)`처럼 자기 자신을 엔티티로 바꾸는 것도 있고, `ProjectRejectRequest`/`ProjectDeadlineExtendRequest`처럼 필드 하나짜리 단순한 것도 있음. |
| `dto/response/ProjectResponse.java` | 응답 DTO. `Project.from(entity)` 정적 팩토리로 엔티티 → DTO 변환. |

**어려운 부분 예시 — `ProjectStatusView`는 왜 있고, 왜 `Project`를 그냥 안 넘기는가**

Reward 도메인이 리워드를 등록/수정할 때 "이 프로젝트 아직 열려있나?", "내가 이 프로젝트 주인 맞나?"를 확인해야 합니다. 이때 두 가지 선택지가 있습니다:

```java
// 선택지 A: Project 엔티티를 통째로 넘긴다
Optional<Project> findProject(Long projectId);
// → Reward가 Project 클래스를 import 해야 함. Project의 title, goalAmount,
//   startAt... 15개 필드를 몰라도 되는데 다 딸려옴. Project 내부 구조가
//   바뀌면 Reward 코드도 흔들릴 수 있음.

// 선택지 B (실제 채택): 필요한 5개 필드만 담은 값 객체를 넘긴다
Optional<ProjectStatusView> findStatusView(Long projectId);
// ProjectStatusView(published, closed, open, status, creatorId)
```

`ProjectStatusView`가 선택지 B입니다. Reward는 `Project` 클래스를 아예 모르고(import도 못 함 — 그런 메서드가 인터페이스에 없으니까), "지금 공개됐어? 닫혔어? 열려있어? 상태가 뭐야? 주인이 누구야?" 이 5개 질문에 대한 답만 받습니다. 이렇게 하면 Project 엔티티에 필드가 몇 개가 추가되든 Reward 코드는 전혀 영향받지 않습니다 — 딱 이 5개 값의 "약속(계약)"만 지키면 되니까요.

---

## 3. Reward 도메인

```
reward/
├── domain/
│   └── Reward.java
├── infrastructure/
│   └── RewardRepository.java
├── application/
│   ├── RewardService.java         (인터페이스)
│   └── RewardServiceImpl.java     (구현체)
└── presentation/
    ├── RewardController.java
    ├── RewardInternalController.java
    └── dto/
        ├── request/RewardCreateRequest.java
        ├── request/RewardUpdateRequest.java
        ├── request/RewardQuantityDecreaseRequest.java
        ├── request/StockChangeRequest.java
        └── response/RewardResponse.java
```

이 도메인의 특징은 **동시성 제어**(재고가 마이너스가 되거나 초과 판매되지 않게)와 **order-service가 직접 호출하는 내부 API**입니다.

| 파일 | 역할 |
| --- | --- |
| `Reward.java` | 엔티티. `@Version`(낙관적 락) 보유 — `decreaseStock`/`restoreStock`/`increaseQuantity`/`decreaseQuantity`가 전부 이 필드로 동시 수정 충돌을 감지한다. `totalQuantity == null`이면 "무제한 리워드"라는 특수 규칙이 있어서, 재고 관련 메서드 전부 이 경우를 먼저 걸러낸다. `isOrderable()`(active && 재고 있음)과 `active`(수동 on/off 플래그)는 서로 다른 개념 — 자세한 건 DOMAIN_GUIDE 참고. |
| `RewardRepository.java` | `JpaRepository` 한 줄. `findByProjectId`, `deleteByProjectId` 등 프로젝트 단위 일괄 처리용 메서드 포함. |
| `RewardService.java` | 인터페이스. CRUD + 재고 증감(`decreaseStock`/`restoreStock`/`decreaseQuantity`) + **Project가 호출하는** `deactivateAllByProject()`/`deleteAllByProject()`. |
| `RewardServiceImpl.java` | 이 파일이 세 도메인 통틀어 `@Retryable` 어노테이션이 제일 많이 붙은 곳 — 재고를 건드리는 메서드마다 "낙관적 락 충돌 나면 최대 3번 재시도" 패턴이 반복된다. 소유권 검증은 `ProjectStatusView.creatorId()`로 한다(자기 창작자 개념이 없어서 부모 프로젝트 걸 빌려씀). |
| `RewardController.java` | `/api/v1/rewards`, `/api/v1/projects/{id}/rewards` 아래 CRUD + 관리자 전용(수량축소/비활성화). |
| `RewardInternalController.java` | `/internal/v1/rewards` — **order-service가 후원 생성/취소 시 직접 호출하는** 재고 차감/복원 API. order-service 쪽 `RewardFeignClient`와 경로가 정확히 일치해야 하는 런타임 계약이라 실수로 바꾸면 안 됨. |
| `dto/request/StockChangeRequest.java` | `RewardInternalController`(내부 API) 전용 — `quantity` 필드 하나. `RewardQuantityDecreaseRequest`(관리자용, 필드명 `amount`)와 이름이 비슷해서 헷갈리기 쉬우니 구분할 것. |

**어려운 부분 예시 — 재시도(`@Retryable`)가 실제로 언제 동작하는가**

리워드 재고가 1개 남았는데, 후원자 A와 B가 거의 동시에 주문한다고 합시다.

```
1. A가 리워드를 읽음 (version=5, 재고=1)
2. B가 리워드를 읽음 (version=5, 재고=1)   ← A가 아직 저장 전
3. A가 재고를 0으로 줄여 저장 → 성공 (version 5→6)
4. B가 재고를 0으로 줄여 저장 시도
   → DB가 "너는 version 5로 저장하려 하는데 지금 이미 6이다" 하고 거부
   → ObjectOptimisticLockingFailureException 발생
5. @Retryable이 이 예외를 잡아서 B의 요청을 처음부터 다시 실행
   (재시도 시 새 트랜잭션에서 최신 데이터(재고=0, version=6)를 다시 읽음)
6. 다시 읽어보니 재고가 0 → "재고 부족" 예외로 정상 실패
```

즉 `@Retryable`은 "동시에 건드려서 저장이 실패한 것"과 "진짜로 재고가 없는 것"을 구분해줍니다. 재시도 안 했으면 B는 실제로는 재고 부족인데 엉뚱하게 "동시 수정 충돌"이라는 알아듣기 힘든 에러를 받았을 거예요.

---

## 4. 도메인 어디에도 안 속하는 공용 파일

| 파일 | 역할 |
| --- | --- |
| `ProjectServiceApplication.java` | 스프링 부트 시작 클래스. `@EnableRetry`(재시도), `@EnableScheduling`(마감 배치), `@EnableFeignClients`(order-service 호출) 세 기능을 여기서 켠다. |
| `config/JpaAuditingConfig.java` | `@EnableJpaAuditing`을 메인 클래스가 아니라 여기 따로 뺌 — 안 그러면 `@WebMvcTest` 슬라이스 테스트가 JPA 없이 뜨려다 죽는다(파일 안 주석에 이유 설명 있음). |
| `exception/ConcurrentUpdateFailedException.java` | Project/Reward 둘 다 쓰는 공용 예외(재시도 다 써도 계속 충돌나면 던짐) — 어느 한쪽 도메인 소유가 아니라 `project-service` 루트 패키지에 둠. |
| `ProjectDataInitializer.java` | 로컬 개발용 시드 데이터(`@Profile("!test")`라 테스트에선 안 돎). 카테고리 트리 + 프로젝트 4개 + 리워드 9개를 앱 시작 시 자동으로 채워 넣는다. `orders.http`가 `rewardId=1`을 가정하고 있어서, 여기 등록 순서를 바꾸면 그 테스트 요청들이 깨진다.

---

## 5. "다른 서비스를 부르는 파일 4개" 세트 — Port / Feign / HttpClient / CircuitBreakerConfig

Project 도메인이 order-service를 부르는 부분(`application/port/OrderPort.java` + `infrastructure/client/*`)은 처음 보면 "왜 파일이 4개나 필요하지?" 싶을 수 있어서, 역할을 명확히 분리해서 설명합니다.

```
OrderPort (인터페이스)              "뭘 물어볼 수 있는지"의 약속
   ↑ implements
OrderHttpClient (어댑터)            "실패하면 어떻게 할지"를 아는 곳
   ↓ 내부에서 사용
OrderFeignClient (Feign 인터페이스)  "진짜 HTTP 요청 모양"
   ↓ Spring Cloud OpenFeign이 자동 생성
(실제 HTTP 호출)                    ──→ order-service
```

**비유**: 손님(`ProjectServiceImpl`)이 배달 앱(`OrderPort`)으로 음식을 시킵니다. 배달 앱은 "가게가 연락이 안 되면 어떻게 할지"(재시도, 환불 안내 등 — `OrderHttpClient`)를 알아서 처리해주고, 그 안에서 실제로 가게에 전화 거는 일(`OrderFeignClient`)은 또 다른 담당자가 합니다. 손님은 이 셋을 신경 쓸 필요 없이 그냥 "배달 앱에 주문했다"고만 알면 됩니다.

각 파일이 몰라도 되는 것:
- **`OrderPort`**: HTTP가 뭔지, 실패하면 어떻게 되는지 **전혀 모름**. `boolean hasOrderedReward(Long projectId)` 메서드 시그니처가 전부.
- **`OrderFeignClient`**: 서킷브레이커가 뭔지 **모름**. 그냥 "이 URL로 이렇게 요청하면 이런 응답이 온다"만 선언.
- **`OrderHttpClient`**: `OrderFeignClient`를 감싸서 실행하되, 실패 시 무엇을 할지(503 던지기)를 결정. `OrderCircuitBreakerConfig`가 정해준 규칙(타임아웃 3초, 실패율 50% 넘으면 잠깐 차단)을 그대로 따름.

**실제로 실패하면 어떤 일이 벌어지는가 (예시)**

```java
public boolean hasOrderedReward(Long projectId) {
    return circuitBreakerFactory.create("order").run(
        () -> orderFeignClient.hasOrderedReward(projectId).data(),  // ① 평소엔 이게 실행
        this::hasOrderedRewardFallback);                            // ② 실패하면 이게 실행
}
```

order-service가 응답을 안 하거나 타임아웃(3초)이 나면 ①이 실패하고 ②(`hasOrderedRewardFallback`)가 대신 실행되는데, 이 fallback은 "주문 없다고 치자"가 아니라 **`ServiceUnavailableException`(503)을 던져서 삭제 자체를 막습니다.** 왜 안전하게(fail-closed) 처리하냐면, 만약 여기서 "확인 안 되니 그냥 삭제 허용"으로 처리했다가 실제로는 주문이 있던 프로젝트였다면 후원자 돈 문제가 생기기 때문입니다 — 결제 로직과 똑같은 원칙("확인 안 됨" ≠ "문제 없음")입니다.
