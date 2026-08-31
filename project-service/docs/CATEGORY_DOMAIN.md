# Category 도메인

담당자: 강대혁
기준일: 2026-08-31

이 문서는 목표 설계가 아니라 현재 `project-service` 구현을 기준으로 Category(프로젝트 카테고리) 도메인의 책임과 처리 흐름을 설명한다.

## 0. 목차

1. 도메인 개요
2. 도메인 모델
3. API
4. 주요 처리 흐름
5. 데이터 저장 구조
6. 예외 처리와 장애 복구
7. 테스트 현황
8. 현재 한계와 후속 과제

> 상태를 가지는 엔티티가 없어 "상태 전이" 장, 발행하는 도메인 이벤트가 없어 "도메인 이벤트" 장은 두지 않는다.

## 1. 도메인 개요

### 1.1 책임 범위

Category는 얼리버드 전체가 공유하는 **전역 taxonomy**다. 프로젝트를 분류하는 축을 제공하고, 그 축이 계층(부모-자식)으로 유지되도록 트리의 무결성을 책임진다. 카테고리 자체는 펀딩·주문·결제와 아무 관련이 없고, 오직 "어떤 분류 체계가 존재하는가"만 관리한다.

- 계층형 카테고리의 생성·수정·삭제 (관리자 전용)와 트리 조회 (공개)
- 트리 무결성 보장 — 존재하지 않는 부모 참조 금지, 자기 자신·자손을 부모로 지정하는 순환 참조 금지, 참조되고 있는 카테고리의 삭제 금지
- 계층 계산 유틸 제공 — 조상 경로 문자열(`패션 > 의류 > 상의`), 하위 트리 전체 id, 루트 id (`CategoryHierarchy`)

이 도메인이 담당하지 않는 범위:

| 범위 | 담당 |
| --- | --- |
| 프로젝트가 어떤 카테고리에 속하는지(`Project.categoryId`) | Project 도메인 |
| 카테고리 기준 프로젝트 목록 필터 | Project 도메인 (`ProjectServiceImpl.findAll`) |
| 검색 시 카테고리 의도 추론 / 카테고리 벡터 임베딩 | Project 도메인의 검색 인프라 (`CategoryIntentResolver`, `ProjectSearchAdapter`) |

`CategoryHierarchy`는 Category 도메인 소유의 값 객체지만, 위 두 소비자가 모두 이 클래스를 직접 쓴다 — 계층 계산 규칙이 여러 곳에 복제되지 않도록 한 곳에만 둔 것이다.

### 1.2 다른 서비스와의 관계

| 연관 대상 | 통신 방향 | 주고받는 정보 | Category의 책임 | 상대 대상의 책임 |
| --- | --- | --- | --- | --- |
| Project 도메인 (같은 서비스) | Project → Category | `existsById(categoryId)`, `findAll()` | 카테고리 존재 여부와 전체 목록 제공 | 프로젝트 생성/수정 시 categoryId 유효성 검증 요청, 하위 카테고리 포함 필터 계산 |
| Project 도메인 (같은 서비스) | Category → Project | `projectRepository.existsByCategoryId()` | 삭제 전 참조 여부 확인 | 참조 존재 여부 응답 |
| chat-service | chat → project-service | `GET /api/v1/project-categories` | 카테고리 트리 응답 | AI 챗봇이 사용자에게 카테고리를 안내할 때 조회 |
| gateway-server | 외부 → gateway → project-service | JWT → `X-User-Role` 헤더 | 헤더의 role이 ADMIN인지 확인 | JWT 검증과 role 클레임의 헤더 투영 |

```
관리자 → gateway → POST/PUT/DELETE /api/v1/project-categories : 트리 변경 (ADMIN)
익명/사용자 → gateway → GET /api/v1/project-categories        : 트리 조회 (공개)
Project 도메인 → ProjectCategoryRepository                     : 존재 검증 / 계층 계산
Category 도메인 → ProjectRepository.existsByCategoryId         : 삭제 전 참조 확인
```

책임 경계: 인증(JWT 검증)은 gateway가, 인가(ADMIN 여부)는 컨트롤러가 한다. 두 곳이 중복 검증하지는 않는다 — gateway는 role을 헤더로 투영만 하고 카테고리 API의 role 요건 자체는 모른다. 삭제 시의 참조무결성 검증은 DB FK가 아니라 애플리케이션이 한다(§5 참고).

### 1.3 기능 범위

| 기능 | 권한 | 비고 |
| --- | --- | --- |
| 카테고리 등록 | ADMIN | 상위 카테고리 미지정 시 루트 |
| 카테고리 트리 전체 조회 | 공개 | 루트부터 재귀적으로 children 중첩 |
| 카테고리 단건 조회 | 공개 | 하위 트리 미포함 |
| 카테고리 수정 (이름·상위 변경) | ADMIN | 순환 참조 거부 |
| 카테고리 삭제 | ADMIN | 하위 카테고리 / 참조 프로젝트가 있으면 거부 |

## 2. 도메인 모델

### 2.1 ProjectCategory

계층형 카테고리 한 노드. **Self-Referencing이지만 JPA 연관관계를 쓰지 않고 부모의 id 값(`parentProjectCategoryId`)만 들고 있다** — 팀 전체 규칙(도메인 경계를 넘는 참조는 ID로만)을 같은 서비스 안 애그리거트 사이에도 동일하게 적용한 것이다. 그래서 트리 순회는 엔티티 그래프 탐색이 아니라 `findAll()` 후 인메모리 계산으로 한다.

| 필드 | 의미 | 제약조건 |
| --- | --- | --- |
| `id` | PK | `IDENTITY` 자동 생성 |
| `parentProjectCategoryId` | 상위 카테고리 id | nullable — `null`이면 루트. FK 없음 |
| `name` | 카테고리 이름 | `NOT NULL`, 공백 불가(`validateName`). **유일 제약 없음** |

`BaseEntity`를 상속하지 않아 생성/수정 시각을 남기지 않는다 — 관리자가 드물게 바꾸는 정적 taxonomy라 감사 이력 요구가 아직 없다.

주요 도메인 규칙:

- 생성: `ProjectCategory.create(parentId, name)` — 이름이 null이거나 공백이면 `IllegalArgumentException`
- 변경: `rename(name)` (같은 이름 검증), `changeParent(parentId)`
- 불변 조건: 이름은 항상 비어 있지 않다. 자기 자신이나 자손을 부모로 가질 수 없다(엔티티가 아니라 서비스가 보장 — 엔티티는 트리 전체를 모른다)

### 2.2 CategoryHierarchy (값 객체)

`List<ProjectCategory>`를 한 번 받아 `id → 카테고리`, `부모 id → 자식 id 목록` 두 맵을 만들어 두고 계층 질의에 답한다. 카테고리 수가 적어(현재 시드 기준 수십 건) 호출 시점마다 `findAll()` 후 인메모리 순회로 충분하다는 판단이고, 캐시는 두지 않았다.

| 메서드 | 반환 | 용도 |
| --- | --- | --- |
| `withDescendants(ids)` | 입력 id + 그 하위 전체 id | 카테고리 필터가 상위 카테고리로도 동작하게 (#761), 검색의 kNN 하드 스코프 |
| `path(id)` | `"패션 > 의류 > 상의"` | `categoryVector` 임베딩 텍스트, 조상 전체를 붙여야 리프가 상위 의미를 잃지 않는다 (#765) |
| `rootId(id)` | 조상 체인 최상단 id | 카테고리 의도 추론에서 "다른 루트 카테고리군과의 격차" 판정 |

세 메서드 모두 `visited` 집합으로 순환을 방어한다 — 서비스가 순환을 막고 있지만, 데이터가 어떤 경위로든 순환하더라도 무한 루프로 서비스가 멈추지는 않게 한 방어선이다. 모르는 id는 예외 없이 그대로 통과시킨다(`withDescendants`는 포함, `path`는 빈 문자열, `rootId`는 입력값 그대로).

## 3. API

### 3.1 외부 API

| Method | Path | 요청 | 응답 | 동작 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/project-categories` | `ProjectCategoryCreateRequest` + `X-User-Role: ADMIN` | `ProjectCategoryResponse` | 카테고리 등록 |
| GET | `/api/v1/project-categories` | — | `List<ProjectCategoryTreeResponse>` | 전체 트리 조회 |
| GET | `/api/v1/project-categories/{id}` | — | `ProjectCategoryResponse` | 단건 조회 |
| PUT | `/api/v1/project-categories/{id}` | `ProjectCategoryUpdateRequest` + `X-User-Role: ADMIN` | `ProjectCategoryResponse` | 이름/상위 변경 |
| DELETE | `/api/v1/project-categories/{id}` | `X-User-Role: ADMIN` | `null` (envelope만) | 삭제 |

모든 응답은 `common`의 `ApiResponseWrappingAdvice`가 `ApiResponse`로 감싼다 — 컨트롤러는 DTO를 그대로 반환한다.

### 외부 API 데이터 스키마

```jsonc
// ProjectCategoryCreateRequest / ProjectCategoryUpdateRequest
{
  "parentProjectCategoryId": 1,   // null이면 루트
  "name": "의류"                   // @NotBlank
}

// ProjectCategoryResponse
{ "id": 7, "parentProjectCategoryId": 1, "name": "의류" }

// ProjectCategoryTreeResponse (GET 목록 — 루트 배열, children 재귀 중첩)
[
  { "id": 1, "parentProjectCategoryId": null, "name": "패션",
    "children": [
      { "id": 7, "parentProjectCategoryId": 1, "name": "의류", "children": [] }
    ]}
]
```

`PUT`은 전체 교체 시맨틱이다 — `parentProjectCategoryId`를 빼고 보내면 "미변경"이 아니라 "루트로 이동"으로 해석된다.

### 3.2 내부 API

없다. 다른 서비스가 카테고리를 필요로 하는 경로는 chat-service의 공개 `GET /api/v1/project-categories`뿐이라 `/internal/v1` 경로를 따로 두지 않았다.

인가 경계: `create/update/delete`는 컨트롤러가 `X-User-Role`이 `ADMIN`인지 직접 확인한다(`requireAdmin`). 카테고리는 전역 taxonomy라 한 명의 변경이 모든 사용자의 목록/트리에 영향을 주기 때문이다. 조회 두 개는 비로그인 사용자도 호출하는 공개 API라 헤더를 요구하지 않는다.

## 4. 주요 처리 흐름

### 4.1 정상 흐름

```
ProjectCategoryController (ADMIN 확인)
→ ProjectCategoryServiceImpl.create/update/delete   [트랜잭션 없음 + treeLock 획득]
→ ProjectCategoryServiceImpl.*Transactional         [프록시 경유, 새 트랜잭션]
→ 트리 검증 (부모 존재 / 순환 / 참조)
→ ProjectCategoryRepository
→ 커밋 후 락 반납
```

세 쓰기 메서드는 전부 **"락 → 트랜잭션" 순서**로 두 겹이다.

1. `create/update/delete` — `@Transactional(propagation = NOT_SUPPORTED)`. 트랜잭션을 열지 않은 채 `synchronized (treeLock)` 블록에 들어간다.
2. 블록 안에서 `selfProvider.getObject().*Transactional(...)`을 호출 — self-invocation은 프록시를 거치지 않아 `@Transactional`이 아예 발동하지 않으므로 `ObjectProvider`로 프록시를 다시 얻는다.
3. `*Transactional` 메서드가 자기 트랜잭션을 열고 검증·저장한 뒤 커밋한다. 커밋까지 끝난 뒤에야 `synchronized` 블록을 빠져나가므로, 다음 스레드는 항상 커밋된 최신 트리를 본다.

`NOT_SUPPORTED`가 필수인 이유: 애노테이션을 생략하면 클래스 레벨 `@Transactional(readOnly = true)`를 상속해 바깥 메서드가 먼저 읽기전용 트랜잭션을 연다. 그러면 `*Transactional`이 `REQUIRED` 전파로 그 트랜잭션에 합류해버려, 변경사항이 커밋 시 플러시되지 않고 **예외 없이 조용히 사라진다.**

메서드별 검증:

| 메서드 | 검증 |
| --- | --- |
| `createTransactional` | `validateParentExists` — 부모 id가 있으면 실제 존재해야 함 |
| `updateTransactional` | 부모가 바뀌는 경우에만 `validateParentExists` + `validateNotSelfOrDescendant`. 이름은 항상 `rename()` |
| `deleteTransactional` | `existsByParentProjectCategoryId` (하위 카테고리), `projectRepository.existsByCategoryId` (참조 프로젝트) |

`validateNotSelfOrDescendant`는 새 부모 id에서 시작해 부모를 따라 루트까지 올라가면서 자기 id가 나오는지 확인한다 — 나오면 새 부모가 내 자손이라는 뜻이라 순환이 생긴다.

`updateTransactional`에서 **이름만 바뀌는 개명은 소속 프로젝트를 재색인하지 않는다.** ES 문서(`ProjectDocument`)가 `categoryName`이 아니라 `categoryId`를 저장하기 때문에, 키워드가 카테고리명과 정확히 일치할 때의 term 매치(`categoryId` 하드 스코프)는 id 기준이라 어긋나지 않는다.

> ⚠️ **다만 임베딩 벡터는 어긋난다.** `ProjectSearchAdapter`가 색인 시 만드는 5개 벡터는 전부 `CategoryHierarchy.path(categoryId)`가 만든 **카테고리 이름 문자열**을 앞에 붙여 임베딩한다(`enrichedTitle = 카테고리 + 제목 + 요약` 등). 개명해도 재색인 트리거가 없어 옛 이름 기준 벡터가 그대로 남는다. 현재 코드의 "재색인 불필요" 판단은 term 매치까지만 맞는 이야기다 — §8 참고.

### 4.2 실패 및 보상 흐름

```
쓰기 요청
→ treeLock 대기 (다른 쓰기가 진행 중이면 직렬 대기)
→ 검증 실패: 트랜잭션 롤백 → 400/404/409 응답, 락 반납
→ 검증 통과: 커밋 → 200 응답, 락 반납
```

- 보상 처리는 없다. 카테고리 쓰기는 외부 호출을 하지 않는 단일 DB 트랜잭션이라, 실패는 곧 롤백이고 남는 부작용이 없다.
- 중복 요청 방지(멱등키)도 두지 않았다. 카테고리 생성은 관리자가 드물게 하는 수동 작업이라 재시도 중복 생성 위험이 프로젝트/리워드만큼 크지 않다고 판단했다.
- 동시 요청은 `treeLock` 하나로 **세 쓰기 메서드가 서로 배타적으로** 실행되게 해서 막는다. 세 개가 같은 락을 공유해야 하는 이유는 §6에 정리했다.

## 5. 데이터 저장 구조

```
ProjectCategoryServiceImpl
→ ProjectCategoryRepository (JpaRepository)
→ Hibernate / MySQL
→ project_categories
```

| 항목 | 내용 |
| --- | --- |
| 테이블 | `project_categories` (`id`, `parent_project_category_id`, `name`) |
| 외래 키 | **없다.** `parent_project_category_id`는 값 참조일 뿐이고, `projects.category_id` 역시 FK가 아니다 |
| 유니크 | 없다. 같은 이름의 카테고리가 여러 개 존재할 수 있다 |
| 체크 제약 | 없다. 이름 공백 검증은 엔티티가 한다 |
| 동시성 제어 | DB 락이 아니라 **애플리케이션 JVM 락**(`treeLock`, `synchronized`) |
| 같은 트랜잭션에서 처리해야 하는 데이터 | 검증(자식 존재, 프로젝트 참조)과 실제 삭제는 같은 트랜잭션 + 같은 락 구간 안에 있어야 한다 |

FK를 두지 않아 참조무결성을 애플리케이션이 대신 지킨다. 그래서 `deleteTransactional`의 두 체크가 사실상 `ON DELETE RESTRICT` 역할이다 — 이 체크 없이 지우면 자식 카테고리는 트리에서 조용히 사라지고(루트로도 안 올라오고 부모의 children에도 안 잡힘), 프로젝트는 존재하지 않는 `categoryId`를 가리키게 된다.

`ProjectCategoryRepository`의 커스텀 메서드:

| 메서드 | 호출부 |
| --- | --- |
| `existsByParentProjectCategoryId` | `deleteTransactional` — 하위 카테고리 존재 확인 |
| `findByNameIgnoreCase` | `ProjectSearchAdapter.resolveExactCategoryIds` — 검색어가 카테고리명과 정확히 일치할 때 kNN 하드 스코프 |

반대 방향으로 `ProjectRepository`에도 카테고리용 메서드가 두 개 있다. `existsByCategoryId`는 삭제 전 참조 확인에 실제로 쓰이지만, **`findByCategoryId`는 호출하는 곳이 없다** — javadoc에는 "카테고리 이름 변경 시 소속 프로젝트를 전부 재색인하기 위한 조회(`ProjectCategoryServiceImpl.updateTransactional()`)"라고 적혀 있으나 그 재색인 배선이 없다(§8 참고).

## 6. 예외 처리와 장애 복구

| 장애 상황 | 판별 기준 | 처리 방식 | 최종 상태 |
| --- | --- | --- | --- |
| 존재하지 않는 카테고리 조회/수정/삭제 | `findById` empty | `EntityNotFoundException` | 404 |
| 존재하지 않는 상위 카테고리 지정 | `existsById(parentId)` false | `EntityNotFoundException` | 404 |
| 자기 자신/자손을 부모로 지정 | 조상 체인 순회 중 자기 id 발견 | `IllegalArgumentException` | 400 |
| 이름 공백 | `validateName` | `IllegalArgumentException` | 400 |
| 하위 카테고리 있는데 삭제 | `existsByParentProjectCategoryId` true | `IllegalStateException` | 409 |
| 참조 프로젝트 있는데 삭제 | `existsByCategoryId` true | `IllegalStateException` | 409 |
| ADMIN 아님 | `X-User-Role != ADMIN` | `IllegalArgumentException` | 400 |

상태 코드 매핑은 `common`의 `GlobalExceptionHandler`가 일괄 처리한다(`IllegalArgumentException` → 400, `IllegalStateException` → 409, `BusinessException`은 자기 status). 카테고리 도메인에는 전용 예외 클래스가 없다.

**동시성 방어가 곧 이 도메인의 장애 대응이다.** `treeLock` 하나로 세 메서드를 직렬화하는 이유:

- `create`/`update`가 동시에 서로를 부모로 지정하면(A→B, B→A) 둘 다 상대의 커밋 전 상태를 보고 검증을 통과해 순환이 생긴다.
- `update`와 `delete`가 겹치면, 방금 부모로 지정하려는 카테고리가 그 사이 삭제돼 존재하지 않는 id를 가리키게 된다 (참조무결성 체크와 실제 삭제 사이의 TOCTOU).

대안으로 DB 낙관적/비관적 락도 가능했지만, 카테고리 변경은 관리자 전용의 드문 작업이라 JVM 레벨 직렬화로 충분하다고 판단했다. **단일 인스턴스 전제**다.

복구 배치는 없다 — 자동 복구가 필요한 비동기 처리나 외부 연동 자체가 없다.

## 7. 테스트 현황

| 테스트 | 검증 범위 |
| --- | --- |
| `ProjectCategoryServiceImplTest` | 생성/조회/트리 구성/수정, 부모 존재 검증, 순환 참조 거부 |
| `ProjectCategoryServiceImplDeleteTest` | 하위 카테고리 존재 시 거부, 참조 프로젝트 존재 시 거부, 정상 삭제 |
| `ProjectCategoryConcurrencyIntegrationTest` | `treeLock` 직렬화 — 동시 생성/수정/삭제가 순환·TOCTOU를 만들지 않는지 |
| `CategoryHierarchyTest` | `withDescendants` / `path` / `rootId`, 순환 데이터에서의 무한루프 방어, 모르는 id 처리 |
| `ProjectCategoryControllerTest` | ADMIN 권한 체크, `@Valid` 검증, 응답 envelope |

테스트하지 못한 시나리오:

- **다중 인스턴스에서의 동시 트리 변경.** `treeLock`이 JVM 로컬이라 인스턴스가 2개 이상이면 직렬화가 깨지는데, 단일 인스턴스 테스트로는 재현되지 않는다.
- 카테고리 트리가 수천 건으로 커졌을 때 `findAll()` 기반 인메모리 계산의 성능.

## 8. 현재 한계와 후속 과제

- **`treeLock`이 JVM 로컬 락이라 단일 인스턴스에서만 유효하다.** 다중 인스턴스로 스케일아웃하면 두 인스턴스가 각자 락을 잡고 동시에 트리를 바꿔 순환·고아 참조가 생길 수 있다.
    - 대응 계획: 스케일아웃 시점에 분산 락(Redis) 또는 DB 비관적 락으로 교체. 지금은 관리자 전용의 드문 작업이라 우선순위를 낮게 뒀다.
- **카테고리 이름에 유일 제약이 없다.** 같은 이름의 카테고리를 여러 개 만들 수 있고, `findByNameIgnoreCase`가 `List`를 반환하는 것도 그래서다. 검색의 정확 카테고리명 매칭은 동명 카테고리를 전부 스코프에 넣는데, 이는 현재로선 의도된 동작이지만 운영자가 실수로 중복 생성하는 것을 막지는 못한다.
    - 대응 계획: `(parent_project_category_id, name)` 유일 제약 추가를 검토. 기존 데이터 정리가 선행돼야 한다.
- **FK가 없어 참조무결성이 전적으로 애플리케이션 검증에 달려 있다.** DB에 직접 접근하는 어떤 경로(수동 SQL, 마이그레이션 스크립트)도 이 규칙을 우회할 수 있다.
    - 대응 계획: Project와 Reward를 별도 서비스로 분리할 계획이 있어 `projects.category_id`에는 FK를 걸지 않는 게 맞지만, `project_categories`의 자기참조 FK는 걸어도 무방하다 — 별도 이슈로 검토.
- **카테고리를 개명해도 소속 프로젝트의 임베딩 벡터가 갱신되지 않는다.** 5개 벡터가 전부 카테고리 이름을 앞에 붙여 임베딩한 것이라 옛 이름 기준으로 남는다. `categoryId` term 매치는 영향이 없어 증상이 조용하고, 전체 재색인(`POST /api/v1/projects/reindex`) 전까지 벡터 검색 품질이 미세하게 어긋난다.
    - 대응 계획: `updateTransactional`에서 이름이 바뀐 경우 `ProjectRepository.findByCategoryId`로 소속 프로젝트를 조회해 재색인한다. 그 메서드는 이미 이 용도로 추가돼 있으나 **현재 호출부가 없는 죽은 코드**다 — 배선을 다시 잇거나, 잇지 않을 거면 메서드와 javadoc을 삭제해야 한다.
- **카테고리 변경 이력이 남지 않는다.** `BaseEntity`를 상속하지 않아 누가 언제 트리를 바꿨는지 추적할 수 없다.
    - 대응 계획: 관리자 감사 로그 요구가 생기면 `BaseEntity` 상속 + 변경 이력 테이블 추가.
- **`CategoryHierarchy`가 호출 시점마다 `findAll()`을 한다.** 검색 경로(`resolveExactCategoryIds`, `resolveCategoryHierarchy`)는 요청당 여러 번 호출하므로 카테고리가 커지면 반복 조회 비용이 눈에 띈다.
    - 대응 계획: `CategoryIntentResolver`의 카테고리 벡터 캐시와 함께 계층 스냅샷 캐싱을 같이 도입한다 (검색 후속 정리 항목).
