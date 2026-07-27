# project-service 작업 기록 — Category / Project / Reward 도메인

- 담당: 강대혁 (Project/Reward)
- 범위: 이 문서의 모든 작업은 `project-service/` 안에서만 이루어졌습니다. 다른 서비스 모듈은 건드리지 않았습니다 (order-service 쪽 필요 조치는 보고만 하고 직접 수정하지 않음 — 7.6절 참고).

---

## 1. 전체 요약

`project-service` 안에 세 도메인(Category, Project, Reward)을 두는 것을 목표로,
1. **Category**를 package-by-feature 구조로 신규 구현
2. 기존에 레이어드(계층별) 구조로 이미 존재하던 **Project 도메인을 완전히 교체**(삭제 후 재작성) — Category와 동일한 패키지 컨벤션으로
3. Project에 딸려 있던 **Elasticsearch 검색 연동을 제거**(추후 필요 시 재구현 예정)
4. Category에 **PUT(수정) 엔드포인트 추가** + **실제 애플리케이션 기동 후 curl로 CRUD 동작 검증**

**Reward는 이 시점(1~6절)에는 작업 범위에 포함되지 않았습니다.** 기존 Reward 코드(레이어드 구조)는 그대로 두었었고, 이후 별도 세션에서 처리했습니다 — 7절 참고.

---

## 2. Category 도메인 (신규)

### 2.1 요구사항
- 계층형 구조 (Self-Referencing): `id`, `parentCategoryId`(nullable), `name`
- 패키지 구조: `com.growmighty.lectures.firstday.project.category` 하위에 `controller / service / repository / domain / dto(request/response)`
- 공통 응답 포맷 `ApiResponse<T>`는 `common` 모듈에 이미 있는 것을 재사용 (새로 안 만듦)
- 순환 참조 방지 검증(자기 자신·자손을 부모로 설정 불가) 포함

### 2.2 패키지 구조
```
project/category/
├── presentation/
│   ├── ProjectCategoryController.java
│   └── dto/
│       ├── request/ProjectCategoryCreateRequest.java
│       ├── request/ProjectCategoryUpdateRequest.java
│       ├── response/ProjectCategoryResponse.java (단건 조회/생성/수정)
│       └── response/ProjectCategoryTreeResponse.java (findAllAsTree 전용)
├── application/
│   ├── ProjectCategoryService.java        (interface)
│   └── ProjectCategoryServiceImpl.java
├── infrastructure/ProjectCategoryRepository.java
└── domain/ProjectCategory.java
```
(계층명은 CLAUDE.md에 명시된 프로젝트 표준 `presentation/application/domain/infrastructure` 컨벤션을 따름 — 최초 작성 시 `controller/service/repository`로 적었다가 표준에 맞춰 리네임했고, 클래스명도 다른 도메인과의 혼동을 막기 위해 `ProjectCategory*` 접두사로 통일함. 이 문서는 리네임 후 기준으로 갱신됨.)

### 2.3 `ProjectCategory` 엔티티
- 테이블: `categories`
- 필드: `id`(PK, IDENTITY), `parentCategoryId`(Long, nullable — **JPA 연관관계가 아니라 스칼라 값**으로만 부모를 참조), `name`
- 생성/이름변경/부모변경을 위한 정적 팩토리(`create`) + 도메인 메서드(`rename`, `changeParent`, `isRoot`) 제공
- `parentCategoryId`를 `@ManyToOne` 연관관계로 만들지 않은 이유: 순환참조 검증 로직(부모 체인을 id로만 타고 올라가는 방식)을 단순하게 유지하기 위함. 트리 조립은 서비스 레이어에서 flat list를 그룹핑해서 재귀적으로 구성.

### 2.4 `ProjectCategoryRepository`
- `JpaRepository<ProjectCategory, Long>` 상속만 (별도 커스텀 쿼리 없음)

### 2.5 `ProjectCategoryService` / `ProjectCategoryServiceImpl`
전체 CRUD를 인터페이스에 정의했으나, **컨트롤러에는 요청받은 범위만 노출**(create/findAllAsTree/findById/update — delete는 서비스 로직은 있지만 컨트롤러에 노출 안 함, 아래 4.3 참고):

- `create(ProjectCategoryCreateRequest)` — 부모 존재 검증(`parentCategoryId`가 있으면 `existsById`) 후 저장
- `findAllAsTree()` — 전체 카테고리를 조회해 `parentCategoryId` 기준으로 그룹핑한 뒤, 루트(`parentCategoryId == null`)부터 재귀적으로 `ProjectCategoryTreeResponse.children`을 채워 트리로 반환. 단건 조회/생성/수정은 트리를 구성하지 않는 `ProjectCategoryResponse`(children 없음)를 대신 반환한다 — 예전엔 이 응답에도 `children`이 있었지만 항상 빈 배열이라 혼란만 줘서(2026-07-24) 트리 전용 타입으로 분리했다.
- `findById(Long)` — 단건 조회, 없으면 `EntityNotFoundException`(common 모듈, → 404 `C003`)
- `update(Long, ProjectCategoryUpdateRequest)` — 이름 변경 + 부모 변경. 부모가 바뀌는 경우에만 존재 검증 + **순환참조 검증**(`validateNotSelfOrDescendant`) 수행
- `delete(Long)` — 구현은 되어 있으나 컨트롤러 미노출 (4.3 참고)

**순환참조 검증 로직** (`validateNotSelfOrDescendant`):
```
newParentCategoryId부터 시작해서 parentCategoryId를 따라 루트 방향으로 계속 거슬러 올라간다.
그 과정에서 categoryId(수정 대상 자기 자신)를 만나면
  → newParentCategoryId가 categoryId의 자손이라는 뜻이므로 순환이 생긴다 → 거부
categoryId == newParentCategoryId면 자기 자신을 부모로 설정하려는 것 → 거부
```
매 단계 `categoryRepository.findById(cursor)`로 조회하는 O(depth) 쿼리 방식. 카테고리 트리 깊이가 일반적으로 얕으므로(수 단계) 성능상 문제 없음.

### 2.6 `ProjectCategoryController` (`/api/v1/project-categories`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/project-categories` | 등록 |
| GET | `/api/v1/project-categories` | 전체 목록 (트리 구조) |
| GET | `/api/v1/project-categories/{categoryId}` | 단건 조회 |
| PUT | `/api/v1/project-categories/{categoryId}` | 이름/부모 변경 (이번 세션에서 추가, 아래 4.1 참고) |

`ProjectCategoryCreateRequest`/`ProjectCategoryUpdateRequest`에 `@NotBlank name` 검증 적용, 위반 시 common의 `GlobalExceptionHandler`가 400으로 처리.

---

## 3. Project 도메인 (기존 완전 교체)

### 3.1 배경
`project-service`에는 이미 레이어드 구조(application/domain/infrastructure/presentation)의 Project 도메인이 존재했습니다. 상태값(`DRAFT/IN_REVIEW/OPEN/CLOSED/GOAL_REACHED/GOAL_FAILED`)과 엔드포인트(`/projects`, `/admin/projects`)가 이번에 요청받은 새 스펙과 달랐고, Elasticsearch 검색 연동까지 딸려 있어 그대로 두면 새 스펙과 공존하며 혼란을 일으킬 상황이었습니다.

**사용자 확인 후 "완전 교체"로 진행** — 기존 Project 관련 파일을 전부 삭제하고, Category와 동일한 package-by-feature 컨벤션으로 새로 작성했습니다.

### 3.2 삭제된 파일 목록
```
project/domain/Project.java
project/domain/ProjectStatus.java
project/domain/ProjectRepository.java
project/domain/event/ProjectChangedEvent.java
project/application/ProjectService.java
project/application/ProjectSearchService.java          (Elasticsearch)
project/application/ProjectSearchSyncService.java       (Elasticsearch)
project/application/dto/ProjectInfo.java
project/application/dto/RegisterProjectCommand.java
project/infrastructure/ProjectJpaRepository.java
project/infrastructure/ProjectRepositoryAdapter.java
project/infrastructure/ProjectElasticSearchRepository.java   (Elasticsearch, ProjectDocument 참조하던 잔존 파일)
project/infrastructure/search/ProjectDocument.java       (Elasticsearch)
project/infrastructure/search/ProjectIndexer.java        (Elasticsearch)
project/infrastructure/search/ProjectSearchRepository.java (Elasticsearch)
project/presentation/ProjectController.java
project/presentation/ProjectAdminController.java
project/presentation/ProjectSearchController.java        (Elasticsearch, /projects/search)
project/presentation/dto/ProjectResponse.java
project/presentation/dto/RegisterProjectRequest.java
src/test/.../domain/ProjectTest.java
src/main/resources/elasticsearch/project-settings.json
```
`Reward`, `RewardRepository`, `RewardService`, `RewardController` 등은 `projectId`를 Long으로만 느슨하게 참조하고 있어 **손대지 않았습니다.** order-service의 `RewardFeignClient` 연동(`/rewards/...` 경로)도 영향 없습니다.

### 3.3 패키지 구조 (신규)
```
project/project/
├── presentation/
│   ├── ProjectController.java
│   ├── ProjectAdminController.java
│   └── dto/
│       ├── request/ProjectCreateRequest.java
│       ├── request/ProjectUpdateRequest.java
│       ├── request/ProjectRejectRequest.java
│       ├── request/ProjectDeadlineExtendRequest.java
│       └── response/ProjectResponse.java
├── application/
│   ├── ProjectService.java        (interface)
│   └── ProjectServiceImpl.java
├── infrastructure/ProjectRepository.java
└── domain/
    ├── Project.java
    ├── ProjectStatus.java
    └── ProjectSort.java
```
(주의: `project.project` 로 "project"가 중첩되는 패키지명 — project-service 모듈의 베이스 패키지가 `com.growmighty.lectures.firstday.project`이고, 그 아래 도메인별 서브패키지를 붙이는 Category와 동일한 컨벤션을 따른 결과입니다. 계층명은 2.2절과 마찬가지로 `presentation/application/domain/infrastructure` 표준을 따름 — 최초 작성 시 `controller/service/repository`였던 걸 리네임 후 기준으로 갱신.)

### 3.4 `Project` 엔티티
- 테이블: `projects`
- 필드(요청받은 그대로): `projectId`(PK, IDENTITY — 필드명 자체가 `id`가 아니라 `projectId`), `creatorId`, `thumbnailId`, `title`, `categoryId`, `summary`, `description`(`@Lob`), `goalAmount`, `fundedAmount`, `startAt`, `endAt`, `status`, `rejectReason`, `submittedAt`, `approvedAt`, `closedAt`, `createdAt`, `updatedAt`
- `createdAt`/`updatedAt`은 Spring Data JPA Auditing으로 자동 채움 — `@CreatedDate`/`@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)`. `ProjectServiceApplication`에 `@EnableJpaAuditing` 추가 (이 프로젝트에서 auditing을 쓰는 최초 사례라 새로 활성화함).
- `ProjectStatus`: `PENDING_REVIEW, REJECTED, IN_PROGRESS, SUCCEEDED, FAILED, CANCELLED` + `isClosed()`(SUCCEEDED/FAILED/CANCELLED면 true)
- 등록 시 항상 `PENDING_REVIEW`로 시작, `submittedAt` 자동 세팅
- 도메인 메서드:
  - `approve()` — PENDING_REVIEW → IN_PROGRESS, `approvedAt` 세팅
  - `reject(reason)` — PENDING_REVIEW → REJECTED, `rejectReason` 세팅
  - `isPublished()` — `status`가 PENDING_REVIEW/REJECTED가 아니면 true (즉, 한 번이라도 승인되어 공개된 적 있으면 true)
  - `updateBeforePublish(...)` — 공개 전이면 모든 필드 수정 가능 (null 필드는 미변경)
  - `updateAfterPublish(summary, description, thumbnailId, endAt)` — 공개 후에는 이 4개 필드만 수정 가능, `endAt`은 **연장만** 허용(과거로 당기면 `IllegalArgumentException`) — ⚠️ 7.5절에서 `endAt`은 창작자 권한에서 완전히 제외되도록 변경됨 (관리자 전용 API로 이전)

### 3.5 `ProjectRepository`
`JpaRepository<Project, Long>` + `JpaSpecificationExecutor<Project>` — 목록 조회의 동적 필터(keyword/categoryId/status)를 `Specification`으로 조립하기 위함. 이 프로젝트에서 `Specification` 사용은 이번이 최초 사례.
추가로 `findByCreatorId`(내 프로젝트), `findByStatus`(관리자 심사 대기 목록) 파생 쿼리 메서드.

### 3.6 `ProjectService` / `ProjectServiceImpl`
- `create(ProjectCreateRequest)` — `categoryId`가 실제 존재하는 카테고리인지 `CategoryRepository.existsById`로 검증 후 등록 (PENDING_REVIEW로 생성)
- `findAll(keyword, categoryId, status, sort)` — `Specification`으로 조건 조립, `ProjectSort`(LATEST/DEADLINE/FUNDED_AMOUNT)를 `Sort`로 변환해 정렬. 페이지네이션은 요청받지 않아 미구현.
- `findById(Long)`
- `update(Long, ProjectUpdateRequest)` — `project.isPublished()`로 분기:
  - 공개 전: `updateBeforePublish` 호출 (categoryId 바뀌면 존재 검증도 다시 수행)
  - 공개 후: `title/categoryId/goalAmount/startAt` 중 하나라도 요청에 들어있으면 `IllegalArgumentException`(400)으로 거부, 나머지는 `updateAfterPublish` 호출
- `delete(Long)` — 참조하는 리워드를 먼저 cascade 삭제한 뒤 프로젝트 삭제 (8.5절에서 추가). "후원(주문) 발생 전만" 검증은 여전히 TODO — order-service 쪽 API가 필요해서 별도 조율 중 (8.7절 참고)
- `findByCreator(Long creatorId)` — `/me`용
- 관리자용: `findByStatus`, `approve`, `reject`

### 3.7 `ProjectController` (`/api/v1/projects`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/projects` | 등록 (PENDING_REVIEW로 생성) |
| GET | `/api/v1/projects` | 목록. Query: `keyword`, `categoryId`, `status`, `sort`(LATEST/DEADLINE/FUNDED_AMOUNT, 기본 LATEST) |
| GET | `/api/v1/projects/me` | 내 프로젝트 목록. `X-User-Id` 헤더로 식별 (TODO: 인증 도입 후 게이트웨이가 채우도록 전환) |
| GET | `/api/v1/projects/{projectId}` | 상세 |
| PATCH | `/api/v1/projects/{projectId}` | 수정 (공개 여부에 따라 허용 필드 다름, 3.6 참고) |
| DELETE | `/api/v1/projects/{projectId}` | 삭제 (항상 가능, TODO: 후원 발생 여부 검증) |

### 3.8 `ProjectAdminController` (`/api/v1/admin/projects`)
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/admin/projects?status=PENDING_REVIEW` | 상태별 목록 (심사 대기 목록 등) |
| POST | `/api/v1/admin/projects/{projectId}/approve` | 심사 승인 (PENDING_REVIEW → IN_PROGRESS) |
| POST | `/api/v1/admin/projects/{projectId}/reject` | 심사 반려 (PENDING_REVIEW → REJECTED, body: `{ "reason": "..." }`) |

관리자 권한 검증은 아직 없음 (TODO 주석 — 인증/Gateway `/admin` 라우팅 확정 후 추가 예정, 기존 코드 컨벤션과 동일한 방식으로 남겨둠).

---

## 4. 이번 세션에서 추가로 처리한 것

### 4.1 Category `PUT` 엔드포인트 추가
`ProjectCategoryServiceImpl.update()`는 이미 순환참조 검증까지 구현되어 있었지만 컨트롤러에 노출되어 있지 않았음 → `PUT /api/v1/project-categories/{categoryId}` 추가해서 노출 완료.

### 4.2 시드 데이터 계층화 (`ProjectDataInitializer`)
기존에는 플랫한 카테고리 4개(패션잡화/전자기기/도서·출판/반려동물)만 만들어서 Project가 참조했으나, Category 트리 기능을 실제로 확인하기 위해 계층 구조로 확장:
```
패션
├── 의류
│   ├── 상의
│   └── 하의
└── 잡화
    └── 액세서리        ← 프로젝트1(가죽 노트커버)이 참조

전자기기
├── 스마트기기          ← 프로젝트2(빔프로젝터)가 참조
└── 생활가전

도서·출판
├── 시·에세이           ← 프로젝트3(시집)이 참조
└── 독립출판

반려동물
└── 반려용품            ← 프로젝트4(급식기)가 참조
```
프로젝트는 리프(최하위) 카테고리를 참조하도록 배선. Reward ID 시퀀스(1~9번, orders.http가 rewardId=1을 가정)는 그대로 유지됨 — 카테고리/프로젝트 생성 순서를 바꿔도 Reward는 별도 테이블 시퀀스라 영향 없음.

### 4.3 Category `DELETE`는 아직 미구현 (의도적)
요청 목록에 없었기 때문에 만들지 않았습니다. `ProjectCategoryServiceImpl.delete()` 메서드 자체는 존재하지만 자식 카테고리가 있는 경우, 또는 Project가 참조 중인 카테고리인 경우에 대한 정책이 없는 상태라 컨트롤러에 노출하지 않았습니다. **정책 결정 필요** (차단 / cascade 삭제 / 자식을 상위로 재배치 중 택1).

### 4.4 Elasticsearch 의존성/설정 제거
- `build.gradle`: `spring-boot-starter-data-elasticsearch` 제거
- `application.yml`: `spring.elasticsearch.*` 설정 제거
- 검색 관련 코드(3.2절 참고)는 전부 삭제, 필요 시 나중에 새 Project 엔티티 필드에 맞춰 재구현 예정 (이번 세션 범위 아님)

### 4.5 `build.gradle` 정리 (order-service 패턴 반영)
order-service와 동일 패턴으로: `webmvc, data-jpa, h2console, eureka-client, openfeign, config, actuator, circuitbreaker-resilience4j` + Reward 낙관적 락 재시도 대비 `spring-retry`, `spring-aspects` 추가. `common` 모듈은 `project(':common')`으로 참조 (기존 유지).

### 4.6 실제 애플리케이션 기동 + curl 테스트
포트 8081/8761/8888에 이미 다른 프로젝트(`lecture-examples/product-service`, IntelliJ로 실행 중)가 떠 있어서 충돌을 피하기 위해 격리 옵션으로 기동:
```
./gradlew :project-service:bootRun --args='--server.port=8092 --spring.cloud.config.enabled=false --eureka.client.enabled=false'
```
테스트 결과:
- `POST /api/v1/categories` — 정상 생성
- `GET /api/v1/categories` — 트리 구조 정상 (3단계 깊이까지 확인)
- `GET /api/v1/categories/{id}` — 단건 조회 정상
- `PUT /api/v1/categories/{id}` — 이름 변경 정상, 부모 변경(카테고리 이동) 정상
- `PUT /api/v1/categories/{id}` 순환참조 시도(자손을 부모로 지정) → `400 Bad Request`, `"자손 카테고리를 상위 카테고리로 설정할 수 없습니다"` 정상 거부
- `PUT /api/v1/categories/{id}` 자기 자신을 부모로 지정 → `400 Bad Request`, `"자기 자신을 상위 카테고리로 설정할 수 없습니다"` 정상 거부
- `GET /api/v1/categories/999`(존재하지 않는 id) → `404 Not Found`, `code: C003` 정상
- 회귀 확인 — `GET /api/v1/projects`(keyword/categoryId 필터), `GET /rewards/{id}` 모두 정상 동작 확인
- 테스트 후 애플리케이션 정상 종료, 포트 회수 확인 완료

전체 컴파일(`./gradlew compileJava`, 모노레포 전체) 및 `project-service` 테스트(`./gradlew :project-service:test`) 모두 통과.

---

## 5. 팀 문서 검토 중 발견된 이슈 (미해결 / 팀 확인 필요)

### ✅ URL prefix 규칙 불일치 — 해결됨 (2026-07-16)
API 명세서 안에 두 가지 패턴이 혼재해 있었음:
- §0.1절 규칙: `/api/{서비스}/v1/xxx` (예: `/api/users/v1/xxx`)
- 실제 본문 예시 대부분(Project/Reward 섹션 포함): `/api/v1/{서비스}/xxx` (예: `/api/v1/projects`)

**최종 결론(팀 확인): `/api/v1/{서비스}/xxx`가 맞는 규칙.** 이미 이 패턴으로 구현되어 있던 경로들 그대로 유지, 변경 불필요:
- Category: `/api/v1/project-categories`
- Project: `/api/v1/projects`, `/api/v1/admin/projects`
- Reward: `/api/v1/projects/{projectId}/rewards`, `/api/v1/rewards/{rewardId}`, `/api/v1/admin/rewards/{rewardId}`

§0.1절 쪽 문구는 죽은 규칙 — API 명세서 문서 자체도 나중에 §0.1절을 이 패턴에 맞게 정정하는 게 좋음(코드 변경 아님, 문서 정리).

### ✅ `endAt` 수정 권한 애매함 — 해결됨 (7.5절 참고)
- 이전 확정: 창작자가 자유롭게 연장 가능
- 최근 문서: "endAt (관리자 요청시 가능)" — 관리자가 먼저 요청해야 창작자가 수정 가능한 것처럼 읽힘

**최종 결론(2026-07-15, 이 세션에서 확정): 창작자는 `endAt`을 아예 수정할 수 없고, 관리자 전용 API로만 연장 가능.** `Project.updateAfterPublish`에서 `endAt` 파라미터 제거, 신규 `Project.extendDeadline()` + `PATCH /api/v1/admin/projects/{projectId}/deadline` 추가. 자세한 내용은 7.5절.

### 🟢 Cart 연동 — 별도 API 불필요로 결론
Cart(류민송 담당) 정책: "한정 수량 reward 변동사항 검증", "기간 만료/실패 project의 reward 검증" → Cart는 재고를 차감하지 않고 **조회만** 해서 자기 쪽에서 판단하는 구조로 파악됨. 기존 `GET /api/v1/rewards/{rewardId}`(`remainingQuantity` 포함)로 충분 — **별도 검증 전용 API를 만들 필요 없음**으로 결론.

### 🟢 Payment/Settlement 환불 책임 분리 — 설계 일관성 확인됨
Payment 담당(정창민)의 "일괄 환불은 Payment가 직접 수행, Settlement은 이벤트만 발행" 원칙이, Project 쪽에서 이미 확정한 "Settlement은 판정만, 각 서비스가 자기 데이터는 자기가 직접 처리" 원칙과 동일한 논리로 확인됨. 별도 조치 불필요.

### 담당자 라벨 오류 (팀 문서, 코드와 무관)
"Project_Reward_명세.md" 문서 헤더에 담당자가 "조우진"으로 잘못 기재되어 있었음 (실제로는 강대혁=Project/Reward, 조우진=Board). 사용자가 직접 수정 예정.

---

## 6. 다음 단계 (1~6절 시점 기준 — 이후 갱신은 7.7절 참고)

- ~~**Reward 도메인**: Category/Project와 동일한 package-by-feature 컨벤션으로 재정비~~ → 완료 (7절)
- Category `DELETE` 정책 결정 후 엔드포인트 추가
- URL prefix 확정 후 Category/Project/Reward 경로 일괄 수정 여부 결정
- ~~`endAt` 권한 플로우 확정~~ → 완료, 관리자 전용으로 확정 (7.5절)
- Elasticsearch 검색: 필요 시점에 새 Project 필드(`summary`, `fundedAmount` 등) 기준으로 재구현

---

## 7. Reward 도메인 재정비 (신규 세션 — 2026-07-15)

### 7.1 배경 및 지시사항
Category/Project는 package-by-feature로 정리됐지만 Reward는 여전히 옛 레이어드 구조(`domain/application/infrastructure/presentation`)에 남아 있었음. 다음 5가지를 요청받아 진행:
1. Reward를 `project/reward/` 하위에 Category/Project와 동일한 컨벤션(`controller/service/repository/domain/dto`)으로 재작성, 경로를 `/api/v1/...`로 통일
2. `Reward`에 `@Version`(낙관적 락) + `decreaseStock`/`restoreStock` 도메인 메서드, `totalQuantity`가 null이면 무제한(검증 스킵)
3. 재고 차감/복원 내부 API에 `@Retryable`(낙관적 락 충돌, maxAttempts=3) + 소진 시 `ConcurrentUpdateFailedException`(409)
4. `Project.updateAfterPublish`에서 `endAt` 수정 권한 완전 제거
5. `ProjectAdminController`에 `PATCH .../deadline` 신규 (관리자 전용, 연장만)

작업 전에 order-service의 `RewardFeignClient`가 참조하는 경로를 먼저 확인 — GET 조회 경로가 바뀌면 거기도 맞춰야 할 수 있어서였음 (7.6절 결과 참고).

### 7.2 order-service 연동 조사 결과 (수정 전 확인)
`order-service/.../infrastructure/client/RewardFeignClient.java` 기준:
- `GET /rewards/{rewardId}` — 프리픽스 없음. `/api/v1/rewards/{rewardId}`로 바뀌면 **깨짐** → 사용자에게 확인 후 "project-service만 고치고 order-service 쪽은 보고만" 하기로 결정 (7.6절).
- `POST /internal/rewards/{rewardId}/decrease-stock`, `POST /internal/rewards/{rewardId}/restore-stock` — **이미 지금 코드와 경로가 정확히 일치**. 즉 이번 3번 요청사항은 "신규 API 추가"가 아니라 기존 엔드포인트에 `@Version`+`@Retryable`을 보강하는 작업이었음. 두 경로는 Gateway를 거치지 않는 서비스 간 내부 계약이라 `/api/v1` 프리픽스 대상에서 의도적으로 제외.

### 7.3 패키지 구조 (신규)
```
project/reward/
├── presentation/
│   ├── RewardController.java          (공개 API, /api/v1/...)
│   ├── RewardInternalController.java  (재고 차감/복원, /internal/rewards/...)
│   └── dto/
│       ├── request/RewardCreateRequest.java   (jakarta 검증: @NotBlank/@NotNull/@PositiveOrZero)
│       ├── request/StockChangeRequest.java
│       └── response/RewardResponse.java
├── application/
│   ├── RewardService.java        (interface)
│   ├── RewardServiceImpl.java
│   └── exception/ConcurrentUpdateFailedException.java
├── infrastructure/RewardRepository.java   (JpaRepository 단일 인터페이스 — 기존 port+adapter 분리 제거)
└── domain/Reward.java
```
(계층명은 2.2/3.3절과 동일하게 `presentation/application/domain/infrastructure` 표준을 따름 — 최초 작성 시 `controller/service/repository`였던 걸 리네임 후 기준으로 갱신. `ConcurrentUpdateFailedException`은 `exception/`이 아니라 `application/exception/` 하위에 위치.)
기존 레이어드 파일(`project/domain/Reward*.java`, `project/application/Reward*`, `project/infrastructure/Reward*`, `project/infrastructure/RewardRepositoryAdapter.java`, `project/presentation/Reward*`) 전부 삭제. 기존 `RewardTest.java`도 새 패키지(`test/.../reward/domain/RewardTest.java`)로 이동 + 무제한 리워드 케이스 테스트 2개 추가 (총 10개 테스트).

Category/Project와 마찬가지로 application 계층의 별도 Command/Info DTO는 두지 않음 — request DTO에 `toEntity()`, response DTO에 `from(entity)` 방식 그대로 적용.

### 7.4 `Reward` 엔티티 — `@Version` + 무제한 리워드
- `@Version private Long version;` 추가 (낙관적 락)
- `totalQuantity`/`remainingQuantity`를 `nullable`로 변경. **null이면 무제한 리워드** — `decreaseStock`/`restoreStock`이 수량 검증·증감을 그냥 스킵(no-op)하고 반환. `isOrderable()`도 `totalQuantity == null`이면 항상 `true`.
- `increaseQuantity()`(기존에도 있던, 컨트롤러 미노출 메서드)는 무제한 리워드에 호출하면 `IllegalStateException`으로 방어 처리 (totalQuantity가 null인 채로 `+=` 하면 NPE였을 것을 방지).

### 7.5 Project `endAt` 권한 변경 + 관리자 마감일 API
- `Project.updateAfterPublish(summary, description, thumbnailId)` — `endAt` 파라미터 제거. 창작자는 공개 후 `endAt`을 절대 수정 불가.
- 신규 `Project.extendDeadline(newEndAt)` — 관리자 전용, 현재 `endAt`보다 뒤가 아니면 `IllegalArgumentException`(400).
- `ProjectUpdateRequest.hasPublishOnlyRestrictedField()`에 `endAt != null` 조건 추가 → 창작자가 PATCH에 `endAt`을 넣으면 400으로 거부.
- 신규 `ProjectDeadlineExtendRequest(endAt)` + `ProjectService.extendDeadline()` + `PATCH /api/v1/admin/projects/{projectId}/deadline`.

### 7.6 재고 낙관적 락 재시도 — 구현 및 실기동 중 발견한 버그
- `RewardServiceImpl.decreaseStock`에 `@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))` + `@Transactional`.
- `ProjectServiceApplication`에 `@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)` 추가 — `@Transactional`의 기본 order(`LOWEST_PRECEDENCE`)보다 재시도 어드바이저 순서를 한 단계 높여, 재시도가 트랜잭션을 감싸도록 함 (그래야 매 재시도가 새 트랜잭션에서 엔티티/버전을 다시 읽음).
- `@Recover`로 낙관적 락 소진 시 `ConcurrentUpdateFailedException`을 던지도록 구현. `common/` 모듈은 건드리지 않기로 확인받아, 이 예외는 `IllegalStateException`을 상속해 기존 `GlobalExceptionHandler`가 자동으로 409(`INVALID_STATE`, C002)로 매핑하도록 처리.
- **실기동 검증 중 버그 발견**: `@Recover`가 등록된 `@Retryable` 메서드에서, `retryFor`에 없는 다른 예외(예: 재고 부족 `IllegalStateException`)가 발생해도 Spring Retry가 "재시도 소진"으로 취급해 복구 메서드를 찾으려 하고, 매칭되는 시그니처가 없으면 원래 예외를 삼키고 `ExhaustedRetryException`(500)을 던짐 — 재고 부족이 409 대신 500으로 나오는 문제였음. `@Recover(RuntimeException e, ...) { throw e; }` catch-all을 추가해서 해결 (가장 구체적인 타입에 매칭되는 `@Recover`가 우선 적용되므로 낙관적 락 케이스는 그대로 `ConcurrentUpdateFailedException`으로 감).

### 7.7 실제 애플리케이션 기동 + curl 테스트
포트 8093으로 격리 기동:
```
./gradlew :project-service:bootRun --args='--server.port=8093 --spring.cloud.config.enabled=false --eureka.client.enabled=false'
```
확인한 시나리오:
- 카테고리/프로젝트 생성 → 승인(`IN_PROGRESS`) 정상
- 한정 수량 리워드 등록, 무제한 리워드 등록(`totalQuantity` 생략) 모두 정상
- `GET /api/v1/rewards/{id}` 신규 경로 정상, 구 경로 `GET /rewards/{id}`는 이제 404 (프리픽스 이관 확인)
- `POST /internal/rewards/{id}/decrease-stock` 정상 차감 및 `remainingQuantity` 반영 확인
- 재고 초과 차감 요청 → 최초엔 500(7.6절 버그) → 수정 후 **409** 정상 확인
- 무제한 리워드에 대량 차감/복원 → 검증 스킵, `remainingQuantity`는 계속 `null` 유지 확인
- 창작자가 공개된 프로젝트에 `PATCH .../projects/{id}`로 `endAt` 수정 시도 → **400** 정상 거부
- 관리자 `PATCH .../admin/projects/{id}/deadline` → 연장 정상 반영, 과거로 당기는 요청은 **400** 정상 거부
- 시드 데이터(`ProjectDataInitializer`) 정상 기동, 에러 로그 없음
- `./gradlew :project-service:compileJava :project-service:test` 및 모노레포 전체 `compileJava` 모두 통과 (테스트 10건 전부 통과)

### 7.8 order-service — 수정하지 않고 보고만 함
`order-service/src/main/java/com/growmighty/lectures/firstday/order/infrastructure/client/RewardFeignClient.java` 23번째 줄 근처:
```java
@GetMapping("/rewards/{rewardId}")
ApiResponseBody<RewardApiData> fetchReward(@PathVariable("rewardId") Long rewardId);
```
→ `@GetMapping("/api/v1/rewards/{rewardId}")`로 변경 필요. **의도적으로 이 세션에서는 수정하지 않음** (project-service 범위 밖, 사용자가 별도 세션/담당자에게 맡기기로 결정). `decrease-stock`/`restore-stock` 내부 경로는 이미 일치하므로 order-service 쪽 추가 조치 불필요.

### 7.9 다음 단계 (갱신 — 이후 갱신은 8절 참고)
- ~~order-service `RewardFeignClient.fetchReward()` 경로 수정~~ → project-service 범위 밖, order-service 담당자에게 넘김 (7.8절)
- Category `DELETE` 정책 결정 후 엔드포인트 추가 (미해결, 4.3절)
- ~~URL prefix 팀 확정~~ → 완료, `/api/v1/{서비스}/xxx`로 확정 (5절)
- Elasticsearch 검색 재구현 (필요 시점, 4.4절)
- ~~Reward PATCH(수정)/DELETE(삭제) 엔드포인트 구현~~ → 완료 (8절)

---

## 8. Reward PATCH/DELETE 구현 + 코드리뷰 다각도 반영 + 정책 확정 (신규 세션 — 2026-07-20)

### 8.1 배경
7절에서 Reward는 등록/조회/재고 차감·복원(내부 API)까지만 구현했고, 수정(PATCH)·삭제(DELETE)는 미구현 상태로 남아 있었음. 이번 세션 목표: PATCH/DELETE 구현 → `/code-review`(다각도: 정확성 3개 + 정리/재사용 3개 + 설계 고도 + CLAUDE.md 컨벤션, 검증 단계 포함)로 리뷰 → 발견된 문제 전부 수정 → 브랜치 분리해서 PR까지.

### 8.2 PATCH/DELETE 기본 구현
- `PATCH /api/v1/rewards/{rewardId}`: 공개 전엔 `name`/`description`/`price`/`totalQuantity` 자유 수정, 공개 후엔 `increaseQuantity`(수량 추가)만 허용 — Project의 공개 전/후 필드 제한 패턴을 그대로 따름
- `DELETE /api/v1/rewards/{rewardId}`: 공개 전엔 하드 삭제, 공개 후엔 `active=false`로 비활성화만 (이미 후원 데이터가 리워드를 참조하고 있을 수 있어 레코드 보존)
- `Reward`에 `active` 필드 추가, `isOrderable()`이 `active`도 함께 판단하도록 변경
- `RewardServiceImpl`이 published 여부 판단을 위해 `ProjectRepository`를 직접 참조 (같은 서비스 내 다른 서브도메인 — `ProjectServiceImpl`→`ProjectCategoryRepository`와 동일한 기존 패턴)

### 8.3 코드리뷰에서 발견 및 수정한 문제 7가지
다각도 리뷰(8개 앵글) → 1차 검증(11개 후보 중 9개 생존, 2개는 REFUTED로 제외) → 심각도 순으로 하나씩 수정. `CLAUDE.md`의 "cross-domain은 ID로만 참조" 규칙 위반 후보는, 그 규칙이 서비스 간(inter-service) 경계에 한정된 것이지 같은 서비스 내 서브도메인 간에는 이미 선례(Project↔Category)가 있어 REFUTED됨.

1. **비활성화된 리워드가 재고 차감 경로에서 여전히 주문 가능** — `decreaseStock()`이 `active`를 전혀 안 봐서, DELETE로 "판매 종료" 처리해도 order-service의 내부 재고 차감 API를 통하면 그대로 주문이 성사됨. `decreaseStock()` 맨 앞에 `active` 체크 추가(무제한 리워드 조기 반환보다 먼저 둬서 리워드 종류 무관하게 적용).
2. **`updateBeforePublish`가 이미 판매된 재고를 조건 없이 지워버림** — `totalQuantity` 변경 시 `remainingQuantity = totalQuantity`로 무조건 덮어씀. "공개 전이라 안 팔렸을 것"이라는 가정이 항상 맞지는 않아(주문 흐름이 프로젝트 상태를 확인 안 하는 경로가 있음) 이미 판매된 수량이 사라지는 문제. `soldQuantity()`(=`totalQuantity - remainingQuantity`) 계산해서 보존하고, 판매량보다 적게 설정하면 거부하도록 변경.
3. **프로젝트 공개 여부를 트랜잭션 스냅샷으로 판단** — MySQL REPEATABLE READ에서 트랜잭션 첫 조회 시점에 스냅샷이 고정돼, 관리자가 그 사이 승인해도 옛 상태(PENDING_REVIEW)로 착각할 수 있음. `ProjectRepository`에 공유락(`LOCK IN SHARE MODE`) 조회 메서드(`findByIdForStatusCheck`) 추가해서 항상 최신 커밋 상태를 읽도록 변경 (이 메서드 자체는 `강대혁/project`에 커밋, 사용처는 `강대혁/reward`).
4. **종료된(FAILED/CANCELLED) 프로젝트의 리워드에도 수량 추가 허용** — `isPublished()`가 "한 번이라도 승인된 적 있는지"만 판단해서 `SUCCEEDED`/`FAILED`/`CANCELLED`도 전부 "공개됨"으로 묶임. 기존 `Project.isClosed()`를 재사용해 `update()` 맨 앞에 가드 추가, `IN_PROGRESS`만 수량 추가 허용하고 종료된 상태는 전부 차단.
5. **무제한 리워드에 수량 추가 시도 시 상태코드 오류(409→400)** — `IllegalStateException`을 던져 자동으로 409(Conflict)로 매핑됐는데, 동시성 충돌이 아니라 애초에 성립 안 하는 요청이라 400이 맞음. `IllegalArgumentException`으로 예외 타입만 변경.
6. **PATCH로 리워드 이름을 빈 값으로 바꿀 수 있음** — `RewardCreateRequest.name`엔 `@NotBlank`가 있는데 `RewardUpdateRequest.name`엔 없어서 `{"name": ""}`이 그대로 통과. DTO에 `@NotBlank`를 안 붙인 이유: PATCH에서 `name`은 "안 보내면 그대로 두는" 선택 필드라 `@NotBlank`(null도 거부)를 쓰면 부분 수정 자체가 깨짐 — 대신 `updateBeforePublish()` 안에 `name.isBlank()` 체크 추가(null이면 통과, 값 있으면 빈 값 금지).
7. **고아 리워드를 PATCH/DELETE로 정리할 수 없음** — `register()`가 projectId 존재 검증을 안 하고(기존 TODO), Project 하드 삭제도 참조 리워드를 확인 안 해서(기존 TODO) 둘이 겹치면 부모 없는 "고아" 리워드가 생길 수 있는데, `update()`/`delete()`가 프로젝트 조회 실패 시 404를 던져서 이 고아를 영영 정리할 방법이 없었음. 프로젝트 조회를 `Optional`로 바꿔서 "프로젝트 없음"을 "공개 전"과 동일하게 취급하도록 변경 — 자유 수정/하드 삭제(정리) 가능해짐.

각 항목 모두 실기동(로컬 MySQL)해서 curl로 직접 재현·확인. 도메인 단위 테스트도 각 수정마다 추가.

### 8.4 근본 원인 두 가지도 별도로 수정
8.3-7의 "고아 리워드"는 증상만 완화했을 뿐 원인(TODO 2개)은 그대로였음. 이후 명시적으로 요청받아 원인 자체도 처리:
- `RewardServiceImpl.register()` — `projectRepository.existsById()`로 프로젝트 존재 검증 추가 (`강대혁/reward`)
- `ProjectServiceImpl.delete()` — 프로젝트 삭제 전 `RewardRepository.deleteByProjectId()`로 참조 리워드를 먼저 cascade 삭제 (`강대혁/project`, `RewardRepository`에 파생 삭제 쿼리 추가)

### 8.5 비즈니스 정책 확정: 리워드 등록 허용 범위
`register()`에 남아있던 "등록 가능한 상태인지" TODO를 팀 확인 후 정책으로 확정: `PENDING_REVIEW`/`REJECTED`/`IN_PROGRESS`에서는 등록 가능(프로젝트 공개 후에도 새 리워드 등급 추가 가능), `SUCCEEDED`/`FAILED`/`CANCELLED`(종료)에서만 차단. 기존 `existsById` 존재 검증을 `findProject()`(공유락 조회, 8.3-3절)로 통합해 존재 여부와 종료 여부를 한 번에 확인.

### 8.6 관리자 권한 분리: 공개 후 수량 축소·비활성화
정책 확정: 프로젝트 공개 이후 리워드 수량 축소·비활성화는 크리에이터 권한 밖, 관리자 전용으로 분리. 크리에이터는 여전히 수량 추가(`increaseQuantity`)만 가능.

- `Reward.decreaseQuantity(amount)` 신규 도메인 메서드 (판매량 밑으로는 축소 불가, `increaseQuantity`의 대칭 버전)
- `RewardAdminController` 신규 — `ProjectAdminController`와 동일 패턴(`/api/v1/admin/rewards`):
  - `PATCH /api/v1/admin/rewards/{rewardId}/quantity` — 수량 축소
  - `DELETE /api/v1/admin/rewards/{rewardId}` — 비활성화
  - 둘 다 "공개 중(`IN_PROGRESS`)"인 리워드만 대상, 공개 전/종료는 거부
- 기존 `RewardController.delete()`는 공개 후 요청이 오면 이제 하드 삭제/비활성화 대신 `IllegalStateException`("관리자 전용 API를 이용하세요")으로 거부
- 인증이 아직 없어 `ProjectAdminController`와 동일한 한계 — 지금은 URL 차원의 분리일 뿐, 실제 role 검증은 인증 도입 후 추가 필요(TODO)

### 8.7 브랜치 분리 + PR
도메인 파일 수정 위치 기준으로 커밋을 재구성(`git add -p`처럼 파일 단위/hunk 단위로 나눠 재배치) — Reward 도메인 파일은 `강대혁/reward`, Project 도메인 파일(`ProjectRepository` 공유락 메서드, `ProjectServiceImpl.delete()` cascade)은 `강대혁/project`로 분리. `강대혁/reward`는 `강대혁/project` 위에 얹은 상태(dependency 순서 유지).

- PR #11 (`강대혁/project` → `develop`): Project 도메인 후속 개선 (WORK_LOG 정합성, TODO 명시, 상태 기반 노출 제한, 공유락, cascade 삭제)
- PR #12 (`강대혁/reward` → `develop`): Reward PATCH/DELETE + 8.3절 수정 7건 + 8.5/8.6절 정책
- 기존 원격 `강대혁/project`/`강대혁/reward` 브랜치는 각각 PR #4, #6이 이미 merge된 낡은 상태라 force-push로 덮어씀 (안전 — 그 내용은 이미 develop 히스토리에 영구 보존됨)
- 머지 순서: #11(Project) 먼저 → develop 반영 후 #12(Reward) 처리 (dependency 순서)

### 8.8 다음 단계 (갱신)
- PR #11, #12 리뷰/merge (순서대로)
- Category `DELETE` 정책 결정 후 엔드포인트 추가 (미해결, 4.3절)
- Project 삭제 시 "후원(주문) 발생 여부" 검증 — order-service의 주문 존재 확인 API 필요 (별도 조율 중, 참조 리워드 cascade는 8.4절에서 이미 처리 완료 — 이건 서로 다른 검증)
- 마감 감지 배치(`endAt` 도달 시 이벤트 발행), `SUCCEEDED`/`FAILED`/`CANCELLED` 상태 전이 — Settlement 도메인 판정 로직/계약 확정 후 진행
- 관리자 API(`ProjectAdminController`, `RewardAdminController`) 실제 role 검증 — 인증 도입 후
- Reward 상세조회 응답에 창작자 이름 등 enrichment — user-service의 `GET /internal/v1/users/{userId}` 확정됨, 연동은 아직 미착수
