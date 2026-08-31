# Project 도메인

담당자: 강대혁
기준일: 2026-08-31

이 문서는 목표 설계가 아니라 현재 `project-service` 구현을 기준으로 Project(펀딩 프로젝트) 도메인의 책임과 처리 흐름을 설명한다.

## 0. 목차

1. 도메인 개요
2. 도메인 모델
3. 상태 전이
4. API
5. 주요 처리 흐름
6. 데이터 저장 구조
7. 도메인 이벤트와 서비스 간 통신
8. 예외 처리와 장애 복구
9. 테스트 현황
10. 현재 한계와 후속 과제

## 1. 도메인 개요

### 1.1 책임 범위

Project는 얼리버드의 **펀딩 단위**다. All-or-Nothing 규칙(목표 달성 → 창작자에게 정산, 미달 → 후원자 일괄 환불)에서 "무엇이 성공이고 무엇이 실패인가"를 최종적으로 확정하는 것이 이 도메인의 핵심 책임이다.

- 프로젝트의 등록·수정·삭제·취소, 관리자 심사(승인/반려), 마감일 연장, 조기 마감
- **펀딩 성패 확정** — 마감일이 지난 진행중 프로젝트를 모금액 기준으로 `SUCCEEDED`/`FAILED`로 끊는다. 이 확정이 settlement-service의 정산/환불 파이프라인을 발동시킨다
- **모금액(`fundedAmount`) 유지** — order-service의 push를 기본으로 하고, 유실 대비 pull 보정을 백스톱으로 둔다
- **프로젝트 검색** — Elasticsearch 기반 하이브리드 검색(BM25 + 5개 필드 kNN) + Cohere Rerank 재정렬, 자동완성, 전체 재색인
- 공개 범위 제어 — 심사 대기/반려 프로젝트를 목록·검색·단건 조회에서 가리고, 소유자 본인과 ADMIN에게만 보인다

이 도메인이 담당하지 않는 범위:

| 범위 | 담당 |
| --- | --- |
| 가격·재고·후원 옵션 | Reward 도메인 |
| 분류 체계 | Category 도메인 |
| 주문 존재 여부, 확정 누적 모금액의 원본 | order-service |
| 정산/일괄 환불 실행 | settlement-service |
| PG 취소·환불 | payment-service |
| 파일(썸네일) 실물 저장과 삭제 | file-service |

#### 검색이 왜 Project 도메인 안에 있는가

검색(`project/infrastructure/search/`)은 클래스 20여 개, 외부 연동 3개(Elasticsearch·OpenAI·Cohere)를 가진 이 서비스에서 가장 큰 하위 시스템이다. 그런데도 별도 모듈이나 별도 서비스로 분리하지 않고 Project 도메인의 인프라 계층에 두고 있으며, 이는 의도된 선택이다.

1. **검색 대상이 프로젝트뿐이다.** 색인 문서(`ProjectDocument`)는 프로젝트 1건에 1:1로 대응하고, 검색 결과도 `projectId` 목록이다. 리워드 이름(`rewardNames`)과 카테고리 계층(`categoryVector`)이 색인에 들어가지만 그것들은 **프로젝트를 찾기 위한 부가 정보**이지 독립된 검색 대상이 아니다 — 리워드나 카테고리를 따로 검색하는 기능은 없고 계획도 없다. 소비자가 하나뿐인 것을 별도 모듈로 빼면 인터페이스만 늘고 얻는 게 없다.
2. **인프라 여력이 없다.** 검색을 별도 서비스로 띄우면 인스턴스 하나가 더 필요하고, 그 인스턴스는 ES 클라이언트와 임베딩 모델 클라이언트를 각각 유지해야 한다. 현재 데모 환경은 MySQL 1대 + Elasticsearch 1대 + 마이크로서비스 10여 개를 한 머신에서 돌리고 있어 메모리가 이미 빠듯하다 — 로컬에서 스택을 전부 띄운 상태로 ES 통합 테스트를 돌리면 Docker 메모리(8GB) 제약으로 컨테이너가 OOM(exit 137)으로 죽을 정도다. 서비스를 하나 더 늘리는 비용이 얻는 이점보다 크다.
3. **경계는 이미 인터페이스로 그어져 있다.** 애플리케이션 계층은 `ProjectSearchPort`(index / remove / search / autocomplete / bulkIndex) 하나만 알고, ES·OpenAI·Cohere의 존재를 전혀 모른다. 나중에 분리가 필요해지면 이 포트의 구현체를 HTTP 어댑터로 바꾸는 것만으로 서비스 경계를 그을 수 있다 — 지금 분리하지 않는 것이 나중의 분리를 막지 않는다.

즉 "검색은 Project 도메인이 자기 데이터를 찾는 방식"이라는 위치이며, 그래서 이 문서 §5.4(검색 파이프라인)와 §5.5(색인)에서 다룬다.

### 1.2 다른 서비스와의 관계

| 연관 대상 | 통신 방향 | 주고받는 정보 | Project의 책임 | 상대 대상의 책임 |
| --- | --- | --- | --- | --- |
| order-service | Project → order (Feign) | `GET /internal/v1/orders/{projectId}/ordered-existence`, `/funded-amount` | 삭제 가능 여부 판단, 모금액 pull 보정 | 주문 존재 여부와 확정 누적 총액의 소스오브트루스 |
| order-service | order → Project | `PUT /internal/v1/projects/{projectId}/funded-amount` | 절대값 덮어쓰기(멱등) | 결제 확정/취소 시 즉시 push |
| settlement-service | Project → settlement (Kafka) | `project.status-changed.v1` | 마감 확정 사실 발행 | 정산(SUCCEEDED) / 일괄 환불(FAILED·CANCELLED) 실행 |
| settlement-service | settlement → Project | `GET /internal/v1/projects?status=X` | 상태별 프로젝트 목록 응답 | 정산/환불 대상 조회 |
| file-service | Project → file (Kafka) | `project.deleted.v1` | 삭제 사실 발행 (best-effort) | 연관 파일/S3 오브젝트 정리 |
| file-service | file → Project | `GET /internal/v1/projects/{id}/creator` | 창작자 id 응답 | 파일 업로드/삭제 시 소유권 검증 |
| board-service | board → Project | `GET /internal/v1/projects/{id}/creator` | 창작자 id 응답 | 리뷰 생성 알림 메일 대상 조회 |
| chat-service | chat → Project | `GET /api/v1/projects`, `/{id}`, `/{id}/rewards`, `/project-categories` | 공개 API 응답 | AI 챗봇의 프로젝트 탐색/검색 |
| Elasticsearch | Project ↔ ES | 색인/검색/삭제 | 색인 실패는 삼키고, 검색 실패는 DB LIKE로 강등 | 형태소(nori) 분석, BM25, dense_vector kNN |
| OpenAI Embeddings | Project → OpenAI | 텍스트 → 1536차원 벡터 | 실패 시 벡터 없이 진행 | `text-embedding-3-small` 임베딩 |
| Cohere Rerank | Project → Cohere | 원본 쿼리 + 후보 문서 텍스트 | 실패 시 fusion 순서로 완주 | cross-encoder 관련도 점수 |

```
창작자 → gateway → POST /api/v1/projects                    : 등록 (PENDING_REVIEW)
관리자 → gateway → POST /api/v1/projects/{id}/approve        : 승인 (IN_PROGRESS)
order-service → PUT /internal/v1/projects/{id}/funded-amount : 모금액 push
매일 00:00 배치 → closeExpiredProjects                        : 마감 확정
   → order-service GET funded-amount (pull)
   → Project.closeByDeadline() → SUCCEEDED / FAILED
   → Reward.deactivateAllByProject
   → Kafka project.status-changed.v1 → settlement-service
```

책임 경계와 중복 검증:

- **모금액은 order-service가 소스오브트루스다.** Project는 사본을 들고 있을 뿐이고, 마감 확정 직전에 항상 pull로 다시 확인한 값을 쓴다 — 사본만 믿고 성패를 가르지 않는다
- **삭제 가능 여부는 두 번 확인한다.** `delete()`의 사전 체크는 빠른 실패용이고, 진짜 안전장치는 `deleteInternal()`이 배타 락을 잡은 직후의 재확인이다(§5.3)
- **ES는 콘텐츠·가시성의 소스오브트루스가 아니다.** 검색은 후보 `projectId` 목록만 돌려주고, 필터링·정렬·제목은 MySQL에서 다시 가져온다

### 1.3 기능 범위

| 기능 | 권한 |
| --- | --- |
| 프로젝트 등록 | CREATOR 또는 ADMIN (BACKER 불가) |
| 목록 조회 (키워드 검색 / 카테고리 / 상태 / 정렬) | 공개 (비로그인 포함) |
| 자동완성 | 공개 |
| 단건 조회 | 공개. 비공개 프로젝트는 소유자 본인 또는 ADMIN만 |
| 내 프로젝트 목록 | 본인 |
| 수정 | 창작자 본인 (공개 전/후 허용 필드 다름) |
| 삭제 | 창작자 본인, 후원 이력 없을 때만 |
| 취소 | 창작자 본인 또는 ADMIN |
| 승인 / 반려 / 마감일 연장 / 조기 마감 / 만료 일괄 마감 / 전체 재색인 | ADMIN |
| 모금액 갱신 | order-service (내부망) |
| 상태별 목록 / 창작자 조회 | settlement·board·file-service (내부망) |

## 2. 도메인 모델

### 2.1 Project

| 필드 | 의미 | 제약조건 |
| --- | --- | --- |
| `projectId` | PK | `IDENTITY` |
| `creatorId` | 창작자 사용자 id | `NOT NULL`. gateway가 채우는 `X-User-Id`에서 받는다 |
| `idempotencyKey` | 생성 중복 방지 키 | `NOT NULL`, `updatable = false`, `(creator_id, idempotency_key)` 유일 |
| `thumbnailId` | 썸네일 파일 id | nullable. file-service의 파일 id |
| `title` | 제목 | `NOT NULL`. 검색 색인 대상 |
| `categoryId` | 카테고리 id | `NOT NULL`, FK 없음 |
| `summary` | 요약 | nullable. 검색 색인 대상 |
| `description` | 본문 | `@Lob`. 검색 색인 대상 |
| `goalAmount` | 목표 금액 | `NOT NULL`, 0원 초과 |
| `fundedAmount` | 현재 모금액 | `NOT NULL`, 0으로 시작. 절대값 덮어쓰기(멱등) |
| `startAt` | 시작 일시 | `NOT NULL`, `LocalDateTime` |
| `endAt` | 마감일 | `NOT NULL`, **`LocalDate`** |
| `status` | 상태 | `NOT NULL`, `EnumType.STRING` |
| `rejectReason` | 반려 사유 | nullable |
| `submittedAt` / `approvedAt` / `closedAt` | 제출·승인·마감 시각 | 각 전이 시점에 기록 |
| `createdAt` / `updatedAt` | 감사 시각 | `@CreatedDate` / `@LastModifiedDate` |
| `embedding` | 사전 계산 벡터 | `LONGTEXT`, `EmbeddingConverter`로 float[] ↔ JSON |
| `version` | 낙관적 락 버전 | `@Version` |

`BaseEntity`를 상속하지 않고 `@EntityListeners(AuditingEntityListener.class)`로 직접 감사 필드를 둔다.

**`endAt`이 `LocalDate`인 이유가 이 도메인의 핵심 설계 결정 중 하나다.** 펀딩 기간이 일 단위라 마감도 일 단위로 끊어야 하고, `LocalDateTime`이면 자정이 아닌 시각이 들어와 배치가 "오늘 마감인 것"을 정확히 걸러내지 못한다. `endAt` 당일은 "마감일까지"라는 표현대로 하루 종일 열려 있고, **실제 마감은 그 다음날 00:00**이다(2026-07-22 리뷰 결정).

주요 도메인 규칙:

- **생성**: 목표 금액 0원 초과, 마감일이 시작일 이후, 펀딩 기간 최대 3개월(`MAX_FUNDING_PERIOD_MONTHS` — 토스페이먼츠 환불정책 제약), `idempotencyKey` 필수. 초기 상태는 `PENDING_REVIEW`, `submittedAt` 기록
- **`isPublished()`**: `PENDING_REVIEW`도 `REJECTED`도 아닌 상태 — "한 번이라도 공개된 적이 있는가". 공개 후에는 수정 가능한 필드가 제한된다
- **`isOpen()`**: `status == IN_PROGRESS && now < endAt + 1일 00:00`. **저장된 status만으로는 부족하다** — 마감 시각이 지났는데 배치가 아직 안 돈 구간을 걸러내야 배치 주기와 무관하게 마감 순간 즉시 주문이 막힌다. 성공/실패 확정은 여전히 배치의 몫이다
- **`updateBeforePublish` / `updateAfterPublish`**: null 필드는 미변경. 텍스트가 바뀌면 `embedding = null`로 지워 재계산을 유도한다 — 공개 전은 title/summary/description 중 하나라도, 공개 후는 summary/description 중 하나라도 바뀌면(공개 후엔 title을 못 바꾼다)
- **`extendDeadline`**: 관리자 전용, 뒤로만 연장 가능하고 최대 기간 제약도 다시 검증
- **`updateFundedAmount`**: 증분이 아니라 **절대값 덮어쓰기**라 같은 값으로 여러 번 호출해도 결과가 같다(멱등)
- **`closeEarlyAsSucceeded`**: 목표 미달 상태에서는 거부한다 — 그건 취소(`CANCELLED`, 전원 환불)로 처리할 문제지 조기 마감이 아니다
- **`cancel`**: `IN_PROGRESS`이거나 `SUCCEEDED`일 때만. `FAILED`는 이미 자동으로 환불 파이프라인을 타므로 취소 대상이 아니다

### 2.2 ProjectSort / ProjectStatus (enum)

`ProjectSort`는 정렬 옵션을 Spring Data `Sort`로 변환한다 — `LATEST`(createdAt DESC), `DEADLINE`(endAt ASC), `FUNDED_AMOUNT`(fundedAmount DESC). `ProjectStatus`는 §3 참고.

### 2.3 ProjectStatusView (읽기 뷰)

Reward 도메인이 프로젝트 상태·소유자만 필요할 때 쓰는 뷰다. `Project` 엔티티나 리포지토리를 넘기지 않고 `ProjectService` 경계 안에서만 `published`/`closed`/`open`/`status`/`creatorId`를 노출한다. 조회 시 **공유 락**(`findByIdForStatusCheck`)을 쓰는 것이 중요한데, 일반 조회는 REPEATABLE READ 스냅샷에 갇혀 동시에 승인된 최신 상태를 보지 못하기 때문이다.

### 2.4 ProjectDocument (ES 문서)

검색 인덱스 전용 문서다. `status`를 일부러 넣지 않는다 — 가시성 필터링은 MySQL이 후보 id에 대해 그대로 수행하므로, ES에 status를 넣으면 두 곳이 어긋날 여지만 생긴다.

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `projectId` | long | `@Id` |
| `title` / `summary` / `description` | text (nori `korean` 분석기) | BM25 매치 대상 |
| `categoryId` | long | 비분석 — 검색어가 카테고리명과 **정확히** 일치할 때만 term 매치. 예전 `categoryName`(nori text) 방식은 토큰 일부만 겹쳐도 매치되는 노이즈가 있었다 |
| `rewardNames` | text | 리워드 이름으로도 프로젝트가 찾아져야 해서 필터가 아니라 매치 대상 |
| `titleVector` / `summaryVector` / `descriptionVector` / `categoryVector` / `rewardVector` | dense_vector(1536, cosine) | 5개 독립 벡터 |

필드 매핑은 애노테이션이 아니라 `elasticsearch/project-index-mapping.json`으로 직접 관리한다.

## 3. 상태 전이

```
                    ┌→ REJECTED (반려, 종료 아님)
PENDING_REVIEW ─────┤
                    └→ IN_PROGRESS ─┬→ SUCCEEDED  (마감 시 목표 달성 / 관리자 조기 마감)
                                    ├→ FAILED     (마감 시 목표 미달)
                                    └→ CANCELLED  (창작자·관리자 자진 취소)
                          SUCCEEDED ─→ CANCELLED  (정산 전까지만)
```

| 상태 | 의미 | 진입 조건 |
| --- | --- | --- |
| `PENDING_REVIEW` | 심사 대기 | 생성 직후 |
| `REJECTED` | 심사 반려 | ADMIN `reject(reason)` — `PENDING_REVIEW`에서만 |
| `IN_PROGRESS` | 펀딩 진행중 | ADMIN `approve()` — `PENDING_REVIEW`에서만. `approvedAt` 기록 |
| `SUCCEEDED` | 목표 달성 | 마감 배치 `closeByDeadline()`에서 `fundedAmount >= goalAmount`, 또는 ADMIN `closeEarly()` |
| `FAILED` | 목표 미달 | 마감 배치에서 `fundedAmount < goalAmount` |
| `CANCELLED` | 중단 | 창작자 또는 ADMIN `cancel()` — `IN_PROGRESS` 또는 `SUCCEEDED`에서만 |

- `isClosed()` = `SUCCEEDED | FAILED | CANCELLED`. 종료된 프로젝트에는 리워드를 추가·수정할 수 없다
- `isPublished()` = `PENDING_REVIEW`도 `REJECTED`도 아님. **`REJECTED`는 종료가 아니라 미공개**로 취급한다 — `isClosed()`에 포함되지 않는다
- **`SUCCEEDED → CANCELLED`가 허용되는 이유**: "성공은 했지만 창작자가 배송 등을 감당하지 못하게 된" 경우를 위한 것이다. settlement가 `CANCELLED`를 `FAILED`와 동일하게 `projectId` 기준 일괄 환불 대상으로 다룬다.
  ⚠️ 정책상으로는 "정산(창작자 지급) 전까지만" 허용하는 것이 의도지만, **코드로 강제되지는 않는다.** `Project.cancel()`은 `status`만 보고, project-service는 정산 진행 여부를 아예 모른다(settlement를 조회하는 경로가 없다). 즉 이미 정산이 끝난 `SUCCEEDED` 프로젝트도 지금은 취소된다 — §10 참고
- **금지**: `FAILED` → 무엇이든(이미 환불 파이프라인 진입), `CANCELLED` 재취소, `REJECTED` → `IN_PROGRESS`(재심사 경로 없음), 목표 미달 상태의 조기 마감

## 4. API

### 4.1 외부 API

| Method | Path | 요청 | 응답 | 동작 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/projects` | `ProjectCreateRequest` + `X-User-Id`, `X-User-Role` | `ProjectResponse` | 등록 (CREATOR/ADMIN) |
| GET | `/api/v1/projects` | `keyword`, `categoryId`, `status`, `sort` (전부 선택) | `List<ProjectResponse>` | 목록/검색 |
| GET | `/api/v1/projects/autocomplete` | `keyword` (필수) | `List<ProjectAutocompleteResponse>` | 제목 prefix 자동완성 (최대 10) |
| GET | `/api/v1/projects/me` | `X-User-Id` | `List<ProjectResponse>` | 내 프로젝트 |
| GET | `/api/v1/projects/{id}` | `X-User-Id`, `X-User-Role` (선택) | `ProjectResponse` | 단건 조회 |
| PATCH | `/api/v1/projects/{id}` | `ProjectUpdateRequest` + `X-User-Id` | `ProjectResponse` | 수정 |
| DELETE | `/api/v1/projects/{id}` | `X-User-Id` | `null` | 삭제 |
| POST | `/api/v1/projects/{id}/cancel` | `X-User-Id`, `X-User-Role` | `ProjectResponse` | 자진 취소 |
| POST | `/api/v1/projects/{id}/approve` | `X-User-Role: ADMIN` | `ProjectResponse` | 심사 승인 |
| POST | `/api/v1/projects/{id}/reject` | `ProjectRejectRequest` + ADMIN | `ProjectResponse` | 심사 반려 |
| PATCH | `/api/v1/projects/{id}/deadline` | `ProjectDeadlineExtendRequest` + ADMIN | `ProjectResponse` | 마감일 연장 |
| POST | `/api/v1/projects/{id}/close-early` | ADMIN | `ProjectResponse` | 조기 마감 |
| POST | `/api/v1/projects/close-expired` | ADMIN | `ProjectCloseExpiredResponse` | 만료 프로젝트 일괄 마감 (수동 트리거) |
| POST | `/api/v1/projects/reindex` | ADMIN | `ProjectReindexResponse` | ES 전체 재색인 |

`GET /api/v1/projects`와 `/{id}`는 비로그인 사용자도 호출하는 공개 API라 `X-User-Role`이 없을 수 있다 — 그 경우 `BACKER`로 취급해 공개 프로젝트만 보여준다.

`reindex`가 `/internal/v1`이 아니라 여기 있는 이유: `/internal/v1`은 gateway 라우트가 없는 서비스 간 전용 경로라 사람(ADMIN JWT)이 호출할 방법이 없다. `close-expired`와 같은 컨벤션으로 gateway + ADMIN 체크를 받는 경로에 뒀다.

### 외부 API 데이터 스키마

```jsonc
// ProjectCreateRequest
{
  "thumbnailId": 3,
  "title": "친환경 텀블러",           // @NotBlank
  "categoryId": 7,                    // @NotNull
  "summary": "한 줄 소개",
  "description": "본문",
  "goalAmount": 5000000,              // @NotNull, 0원 초과
  "startAt": "2026-09-01T00:00:00",   // @NotNull, LocalDateTime
  "endAt": "2026-10-01",              // @NotNull, LocalDate — 시작일로부터 최대 3개월
  "idempotencyKey": "uuid"            // 선택
}

// ProjectUpdateRequest — null은 미변경
// 공개 전: 전 필드 / 공개 후: summary, description, thumbnailId 만
{ "title": null, "categoryId": null, "summary": "...", "description": null,
  "thumbnailId": null, "goalAmount": null, "startAt": null, "endAt": null }

// ProjectRejectRequest        { "reason": "가이드라인 위반" }   // @NotBlank
// ProjectDeadlineExtendRequest { "endAt": "2026-10-15" }        // @NotNull, 기존 값보다 뒤

// ProjectResponse
{
  "projectId": 10, "creatorId": 2, "thumbnailId": 3,
  "title": "친환경 텀블러", "categoryId": 7,
  "summary": "...", "description": "...",
  "goalAmount": 5000000, "fundedAmount": 1230000,
  "startAt": "2026-09-01T00:00:00", "endAt": "2026-10-01",
  "status": "IN_PROGRESS", "closed": false,
  "rejectReason": null,
  "submittedAt": "...", "approvedAt": "...", "closedAt": null,
  "createdAt": "...", "updatedAt": "..."
}

// ProjectAutocompleteResponse  { "projectId": 10, "title": "친환경 텀블러" }
// ProjectCloseExpiredResponse  { "processedCount": 3, "closedProjectIds": [1,2,3], "failedProjectIds": [] }
// ProjectReindexResponse       { "totalIndexedCount": 198 }
```

`keyword`는 `@Size(max = 100)`이다 — 키워드가 있을 때마다 OpenAI 임베딩 호출이 하나씩 발생하는데, 비로그인도 호출 가능한 공개 API라 길이 상한이 없으면 비용/남용 표면이 무한히 열린다.

### 4.2 내부 API

| Method | Path | 호출 서비스 | 동작 |
| --- | --- | --- | --- |
| GET | `/internal/v1/projects?status=X` | settlement-service | 상태별 프로젝트 목록 (정산/환불 대상) |
| GET | `/internal/v1/projects/{id}/creator` | board-service, file-service | 창작자 id 조회 |
| PUT | `/internal/v1/projects/{id}/funded-amount` | order-service | 모금액 절대값 덮어쓰기 (멱등) |

### 내부 API 데이터 스키마

```jsonc
// FundedAmountUpdateRequest (PUT funded-amount) → 204 No Content
{ "fundedAmount": 1230000 }   // @NotNull @PositiveOrZero — 절대값(누적 총액)

// ProjectCreatorResponse
{ "creatorId": 2 }

// GET ?status=X → List<ProjectResponse> (외부 API와 동일 스키마)
```

신뢰 경계: `/internal/v1`은 gateway 라우트가 없는 내부망 전용이라 JWT도 role 헤더도 요구하지 않는다. `findByStatus`가 외부 관리자 API와 별개로 존재하는 이유는, role 개념이 없는 호출자(settlement)에게 억지로 role을 부여하지 않기 위해서다.

## 5. 주요 처리 흐름

### 5.1 프로젝트 생성 (정상 흐름)

```
ProjectController (CREATOR/ADMIN 확인)
→ ProjectServiceImpl.create              [트랜잭션 없음 — NOT_SUPPORTED]
   1. findByCreatorIdAndIdempotencyKey → 있으면 그대로 반환 (재시도 흡수)
   2. validateCategoryExists
→ ProjectServiceImpl.createInternal      [프록시 경유, 새 트랜잭션]
   3. save → PENDING_REVIEW
   4. searchPort.index(project) → ApplicationEvent 발행
→ 커밋
→ (AFTER_COMMIT, @Async) ProjectSearchIndexEventListener → 재조회 → 임베딩 생성 → ES 색인
```

조회와 삽입을 하나의 `@Transactional`로 묶지 않는다. 정확히 같은 순간 겹치는 진짜 동시 요청이 `(creator_id, idempotency_key)` 유일 제약에서 충돌하면 `DataIntegrityViolationException`을 **트랜잭션 밖에서** 잡아야 하기 때문이다 — 같은 트랜잭션 안에서 catch하면 flush 실패로 이미 rollback-only가 된 트랜잭션의 커밋 시도가 `UnexpectedRollbackException`으로 변질된다. Reward의 `register()`, `RewardStockTransactionExecutor.registerStockChange()`와 같은 패턴이다.

### 5.2 마감 확정 (배치)

```
ProjectDeadlineScheduler  (cron "0 0 0 * * *", Asia/Seoul)
→ ProjectServiceImpl.closeExpiredProjects       [NOT_SUPPORTED — 트랜잭션 없음]
   findByStatusAndEndAtLessThan(IN_PROGRESS, today)
   for each project:
     → closeProjectByDeadline(projectId)         [NOT_SUPPORTED]
        → orderPort.getFundedAmount(projectId)    ← HTTP, 트랜잭션 밖
        → closeProjectByDeadlineInternal(id, amount)  [@Retryable + @Transactional]
             updateFundedAmount → closeByDeadline → SUCCEEDED/FAILED
             deactivateRewards(projectId)
             publishEvent(ProjectClosedEvent)
     → 실패하면 로그만 남기고 다음 프로젝트로 (격리)
→ ProjectCloseExpiredResponse(성공/실패 목록)
```

이 흐름에 세 겹의 설계 의도가 있다.

1. **배치 전체를 한 트랜잭션으로 묶지 않는다.** 묶으면 개별 재시도가 같은 영속성 컨텍스트를 재사용해 엔티티를 새로 읽지 못하고, try/catch도 flush가 커밋 시점까지 미뤄져 실제로는 격리가 되지 않는다. 프로젝트 하나당 독립된 트랜잭션 + 재시도를 갖게 한다.
2. **`NOT_SUPPORTED`가 필수다.** 애노테이션을 생략하면 클래스 레벨 `@Transactional(readOnly = true)`를 상속하고, `self.closeProjectByDeadline()`이 `REQUIRED`로 그 readOnly 트랜잭션에 합류한다. 그러면 Hibernate가 그 세션의 엔티티를 dirty-checking 대상에서 빼버려 **status 변경이 예외 없이 조용히 커밋되지 않는다.**
3. **HTTP 호출을 트랜잭션 밖에서 끝낸다(#196).** `getFundedAmount()` 응답을 기다리는 동안 DB 커넥션을 물고 있으면 안 된다. 재시도(`@Retryable`)는 결과값만 들고 로컬 갱신만 하는 `*Internal`에 남겨서, 낙관적 락 충돌로 재시도해도 order-service를 다시 호출하지 않는다.

`closeExpiredProjects`가 `endAt < today`만 대상으로 하는 것도 의도다 — `endAt` 당일은 하루 종일 열려 있어야 하므로, `endAt = 오늘`인 프로젝트는 **다음날** 배치가 돌 때 비로소 대상이 된다.

`reconcileFundedAmounts`(1시간마다)도 같은 구조다 — `IN_PROGRESS` 프로젝트마다 pull → 건별 독립 트랜잭션, 실패는 그 프로젝트만 건너뛴다.

### 5.3 삭제 (락 순서 역전 데드락 방지)

```
ProjectServiceImpl.delete                       [NOT_SUPPORTED]
   1. getProject (무락 조회) + validateOwnership
   2. orderPort.hasOrderedReward(projectId)      ← HTTP, 트랜잭션 밖 (#196)
→ ProjectServiceImpl.deleteInternal              [@Transactional]
   3. findByIdForDelete  ← PESSIMISTIC_WRITE, Project를 맨 먼저 선점
   4. validateOwnership 재검증 (락 재획득 사이의 TOCTOU 방어)
   5. orderPort.hasOrderedReward 재확인  ← 진짜 안전장치
   6. rewardService.deleteAllByProject
   7. projectRepository.delete
   8. searchPort.remove → ES 삭제 이벤트
   9. publishEvent(ProjectFilesDeletionRequestedEvent)
→ 커밋 → (AFTER_COMMIT) Kafka project.deleted.v1 → file-service
```

**락 순서 역전 문제**: Reward 쓰기 경로는 `findByIdForStatusCheck()`로 Project를 **공유 락**으로 먼저 잠그고 Reward를 나중에(커밋 시점) 잠근다. `delete()`는 반대로 Reward를 먼저 지우고 Project를 나중에 지우므로, 동시에 겹치면 락 순서가 반대라 데드락이 생길 수 있었다. `deleteInternal()`이 맨 처음 Project를 배타 락으로 선점하면 그 사이 다른 트랜잭션은 공유 락조차 못 걸고 대기하게 돼 경합 자체가 사라진다(PR #79).

**2번의 사전 체크는 빠른 실패용일 뿐이다.** 여기서 false가 나와도 배타 락이 없는 틈에 새 `decreaseStock()`이 끼어들어 주문을 완성시킬 수 있다. 5번이 진짜 안전장치인 근거: `decreaseStock()`은 재고를 깎기 전에 항상 Project를 공유 락으로 잠그므로 배타 락 획득 이후로는 끼어들 수 없고, order-service의 `placeOrder()`는 Order row를 재고 차감 HTTP 호출보다 **먼저** 커밋하므로 그 이전에 완성된 주문은 반드시 이 재확인에 걸린다.

### 5.4 검색 파이프라인 (하이브리드 검색 + 리랭킹)

검색은 **Retrieval → Fusion → Rerank** 3단 구조다. 앞단은 "놓치지 않는 것"(recall), 뒷단은 "제대로 고르는 것"(precision)을 담당하고, 각 단계가 실패해도 그 앞 단계의 결과로 완주한다.

```
GET /api/v1/projects?keyword=여름 원피스
│
├─ ProjectController              keyword @Size(max=100)  ← 임베딩 호출 비용 상한
├─ ProjectServiceImpl.findAll
└─ ProjectSearchPort.search(keyword)          [CircuitBreaker: projectSearch, 10s]
   │
   │ ══ STAGE 0. 전처리 (인메모리, <1ms) ═══════════════════════════════
   ├─ resolveExactCategoryIds(keyword)
   │     검색어가 카테고리명과 정확히 일치하면 → 그 카테고리 + 하위 전체 id
   │     → BM25 filter + kNN filter 양쪽에 "하드 스코프"로 적용
   ├─ QuerySynonymExpander.expand(keyword)
   │     정적 인메모리 동의어 맵 ("냥이"→고양이, "댕댕이"→강아지 …)
   │     → 확장 쿼리는 STAGE 1·2 전용. 리랭커에는 원본을 넘긴다
   │
   │ ══ STAGE 1·2. 두 브랜치 병렬 (searchTaskExecutor = Virtual Thread) ══
   ├─ [Branch 1] BM25 (nori 형태소)                        확장 쿼리
   │     title ×2.0 │ summary ×1.2 │ description ×1.0 │ rewardNames ×1.5
   │     minimum_should_match "2<70%",  최대 200건
   │     하드 스코프가 있으면 categoryId terms filter + should(matchAll)
   │
   └─ [Branch 2] Vector                                    확장 쿼리
         ├─ ProjectEmbeddingService.generateEmbedding(확장 쿼리)
         │     OpenAI text-embedding-3-small → 1536차원   [CB: projectEmbedding, 15s]
         ├─ CategoryIntentResolver.resolveCategoryIntent(queryVector)
         │     카테고리 벡터 캐시와 코사인 비교 → 의도 카테고리 (soft boost 대상)
         │     하드 스코프가 이미 잡혔으면 생략
         └─ 5개 필드 kNN **병렬** (k=20, numCandidates=50, similarity ≥ 0.38)
               titleVector │ summaryVector │ descriptionVector
               categoryVector │ rewardVector
               ES score → raw cosine 환산: max(0, 2·score − 1)
   │
   ├─ (벡터 결과가 전부 비면 → BM25 단독 결과 반환하고 종료)
   │
   │ ══ STAGE 3. Score-aware Hybrid Fusion → 후보 40 ═══════════════════
   ├─ 정규화
   │     BM25   : 포화 곡선  score / (score + 5.0)   ← Min-Max 아님
   │     Vector : raw cosine을 [0,1] clamp 후 그대로 사용
   ├─ 가중합 (총합 1.00)
   │     BM25 0.20 │ title 0.25 │ reward 0.20 │ category 0.15
   │     summary 0.12 │ description 0.08
   ├─ 동적 컷오프: 1위 점수 × 0.35 미만 제거 (하한 0.01)
   │     단, BM25에 명시적으로 매치된 문서는 컷오프 면제
   ├─ Category Intent 소프트 부스트: 생존 문서 중 의도 카테고리 소속에 +0.10
   └─ 점수 내림차순 정렬 → 상위 40건 (RERANK_CANDIDATE_LIMIT)
   │
   │ ══ STAGE 4. 하드 필터 → 리랭킹 ════════════════════════════════════
   ├─ fetchDocumentsByIds(후보 40)     ES에서 문서 텍스트 1회 일괄 조회
   ├─ SeasonalConflictFilter.filter()  강한 계절 충돌만 하드 제외
   └─ Reranker.rerank(원본 쿼리, 후보, 문서)          [CB: projectRerank, 1.5s]
         CohereReranker (rerank-v3.5) — "title summary" 텍스트로 cross-encoder 채점
         관련도 컷: 절대 < 0.08 AND 1위 대비 < 35% 인 것만 제거, 1등은 항상 보존
   │
   ▼ 후보 projectId 목록 (관련도 내림차순)
   │
   └─ ProjectServiceImpl: MySQL Specification 재필터
         가시성(비ADMIN은 PENDING_REVIEW/REJECTED 제외) + categoryId(하위 포함)
         + status + projectId IN (후보)
         sort 미지정 → ES 관련도 순서 유지 / sort 지정 → DB 정렬
```

#### 단계별 책임과 실패 시 동작

| 단계 | 담당 클래스 | 입력 → 출력 | 타임아웃 | 실패 시 |
| --- | --- | --- | --- | --- |
| 0. 카테고리 하드 스코프 | `ProjectSearchAdapter.resolveExactCategoryIds`, `CategoryHierarchy` | 검색어 → categoryId 목록 | — (DB 조회) | 빈 목록 = 스코프 없음 |
| 0. 동의어 확장 | `QuerySynonymExpander` | 검색어 → 확장 쿼리 | — (인메모리) | 원본 그대로 |
| 1. BM25 | `ProjectSearchAdapter.searchKeywordScored` | 확장 쿼리 → `ScoredDocument` 200건 | `projectSearch` 10s | 서킷 → DB LIKE 폴백 |
| 2. 임베딩 | `ProjectEmbeddingService` | 확장 쿼리 → float[1536] | `projectEmbedding` 15s | 벡터 null → **BM25 단독** |
| 2. 카테고리 의도 | `CategoryIntentResolver` | 쿼리 벡터 → categoryId 목록 | — (인메모리 캐시) | 빈 목록 = 부스트 없음 |
| 2. 5필드 kNN | `ProjectSearchAdapter.searchFieldKnnScored` | 쿼리 벡터 → 필드별 `ScoredDocument` | `projectSearch` 10s | 브랜치 예외 → **BM25 단독** |
| 3. Fusion | `ProjectSearchAdapter.fuseByScore`, `ScoredDocument` | 6개 점수 목록 → 후보 40 | — (인메모리) | 결과 없으면 빈 목록 |
| 4. 계절 필터 | `SeasonalConflictFilter` | 후보 + 문서 → 후보 | — (인메모리) | 예외 시 fusion 순서로 완주 |
| 4. 리랭킹 | `CohereReranker` → `CohereRerankClient` | 원본 쿼리 + 문서 → 재정렬 목록 | `projectRerank` 1.5s | fusion 순서 유지 |
| 5. 재필터 | `ProjectServiceImpl.buildSpecification` | 후보 → 최종 목록 | — (MySQL) | 예외 전파 |

#### 파이프라인을 이렇게 짠 이유

**(1) ES는 후보 생성기일 뿐, 소스오브트루스가 아니다.**
검색은 `projectId` 목록만 돌려주고 콘텐츠와 가시성은 전부 MySQL에서 다시 가져온다. `PENDING_REVIEW`/`REJECTED`로 색인된 문서, 삭제 실패로 남은 stale 문서가 그대로 노출되지 않게 하기 위해서다. 그래서 `ProjectDocument`에는 `status`를 아예 넣지 않는다 — 두 곳에 두면 어긋날 여지만 생긴다. 자동완성도 같은 원칙이라 제목을 ES가 아니라 DB 엔티티에서 가져온다(수정 후 색인이 아직 안 따라잡아도 최신 제목을 준다).

**(2) 쿼리 이원화 — 확장은 recall, 원본은 판단.**
BM25와 임베딩에는 동의어로 확장한 쿼리를 넘겨 후보를 넓게 건지고, 리랭커에는 사용자가 실제로 친 원본 쿼리를 넘긴다. 확장 쿼리를 리랭커에 넘기면 사용자가 쓰지 않은 단어가 관련도 판단에 섞여 들어간다.

**(3) 정규화 방식을 BM25와 벡터에서 다르게 한다.**
BM25는 상한이 없는 점수라 포화 곡선(`score / (score + 5.0)`)으로 [0,1]에 밀어 넣는다. Min-Max를 쓰지 않는 이유는, 후보가 몇 건 없을 때 사소한 점수 차이가 0과 1로 극단화되기 때문이다. 반면 kNN 점수는 이미 코사인 유사도라 clamp만 하고 그대로 쓴다 — 굳이 재정규화하면 절대적 유사도 정보가 왜곡된다.

**(4) 컷오프를 절대값이 아니라 1위 대비 비율로 잡는다.**
쿼리마다 점수 분포가 완전히 다르다. 고정 임계값을 쓰면 어떤 쿼리는 전부 살아남고 어떤 쿼리는 전부 잘린다. 대신 BM25에 명시적으로 매치된 문서는 컷오프를 면제한다 — 사용자가 친 단어가 실제로 문서에 있으면 벡터 점수가 낮아도 보여주는 게 맞다.

**(5) fusion은 최종 순위가 아니라 후보 생성이다.**
가중합만으로는 "강아지 간식" 검색에 산책줄이 상위에 오는 걸 막지 못한다. 그래서 fusion은 상위 40개를 뽑는 데까지만 쓰고, 최종 순위는 cross-encoder(Cohere)가 낸다. bi-encoder(임베딩)는 쿼리와 문서를 따로 벡터화해 비교하지만 cross-encoder는 둘을 함께 읽어 채점하므로 관련도 판단이 훨씬 정확하다 — 대신 느리고 비싸서 40건에만 쓴다.

**(6) 관련도 컷은 절대 점수와 상대 비율이 둘 다 미달일 때만 자른다.**
2026-08-30 실측(시드 198건) 근거: 상위어 쿼리는 후보 전체의 절대 점수가 낮게 깔린다 — `"패션"`은 40건이 0.0589~0.1025, `"책"`은 33건이 0.0986~0.1282다. 절대값만 보면 정상 결과가 통째로 날아간다. 반대로 비율만 보면 `"강아지 간식"`의 하위권(1위 대비 6~8%인 산책줄)처럼 명백한 노이즈를 못 거른다. 둘 다 미달인 것만 잘라야 양쪽이 산다. **1등은 조건과 무관하게 항상 보존한다** — 전부 점수가 낮은 쿼리에서 결과가 통째로 비는 걸 막기 위해서다. 그리고 Cohere가 점수를 주지 않은 후보는 되붙이지 않는다(`top_n = 문서 수`라 정상 응답이면 누락이 없고, 무조건 붙이면 방금 컷한 후보가 그대로 되살아난다).

**(7) `SeasonalConflictFilter`는 리랭커의 안전망이다.**
대부분의 판단은 리랭커가 하고, 이 필터는 "여름 옷 검색에 겨울 롱코트"처럼 명백한 케이스가 나쁜 rerank 콜에도 절대 안 뜨게 보장한다. 제외 조건은 **AND 둘 다** 만족할 때뿐이다 — ① 쿼리가 계절을 명확히 함의(여름/겨울 중 한쪽만), ② 문서 title/summary에 반대 계절의 강한 마커. 애매하면 아무것도 하지 않는다.

**(8) 모든 외부 의존이 없어도 검색은 동작한다.**

| 사라진 것 | 남는 동작 |
| --- | --- |
| Cohere (`enabled=false` 또는 장애) | `NoOpReranker` — fusion 순서 그대로 |
| OpenAI (키 없음 또는 장애) | 벡터 브랜치 전멸 → BM25 단독 |
| Elasticsearch (장애) | `projectRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc` — DB `title LIKE` 폴백 (와일드카드 이스케이프) |
| 전부 | 키워드 없는 목록 조회는 애초에 MySQL만 쓰므로 무영향 |

CI와 부하 테스트는 `cohere.rerank.enabled=false` + OpenAI 키 없음 조합으로 도므로 실질적으로 BM25 경로를 검증한다(§9의 한계 참고).

**(9) 병렬화는 Virtual Thread 위에서 한다.**
BM25 1회 + 임베딩 1회 + kNN 5회가 전부 blocking I/O라 순차 실행하면 지연이 그대로 누적된다. `ProjectSearchExecutorConfig`가 `spring.threads.virtual.enabled`에 맞춰 `SimpleAsyncTaskExecutor(virtualThreads=true)`를 만들고, 모든 `CompletableFuture.supplyAsync`가 이 executor를 쓴다. `ForkJoinPool.commonPool()`을 그대로 쓰면 중첩 `CompletableFuture`와 blocking I/O가 겹쳐 worker 스레드 고갈(Thread Starvation)이 난다.

#### 자동완성 (별도 경로)

하이브리드 검색과 완전히 분리된 가벼운 경로다. 임베딩도 리랭킹도 쓰지 않는다.

```
GET /api/v1/projects/autocomplete?keyword=친환경
→ ProjectSearchAdapter.doAutocomplete       [CB: projectAutocomplete, 800ms]
   공백 분리한 각 단어에 대해 title prefix must 매치 (case-insensitive), 최대 50건
→ ProjectServiceImpl.autocomplete
   MySQL findAllById → isPublished 필터 → projectId 오름차순 → 최대 10건
   제목은 DB 엔티티에서 가져온다
→ 실패 시 빈 목록 (ES 장애 시 자동완성만 조용히 비활성)
```

정렬 기본값도 검색 경로에서 다르다 — 키워드 검색에서 `sort`를 명시하지 않으면 최신순이 아니라 **ES 관련도 순서**를 그대로 보여준다(검색창의 일반적 UX). `sort`를 명시하면 그 선택을 존중해 DB 정렬 경로를 탄다.

### 5.5 색인 (비동기)

```
ProjectServiceImpl.create/update/delete
→ searchPort.index/remove → ApplicationEventPublisher (도메인 트랜잭션 안)
→ [커밋]
→ ProjectSearchIndexEventListener  @Async @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)
   → ProjectRepository로 "지금" 다시 조회 (이벤트가 실어온 값이 아님)
   → 이미 삭제됐으면 건너뜀
   → ProjectSearchAdapter.applyIndex
      → 리워드 이름 조회 + CategoryHierarchy.path(categoryId)
      → ProjectEmbeddingService.generateFieldVectors  (프로젝트당 1회 배치 임베딩 → 5벡터)
      → elasticsearchOperations.save
```

- **AFTER_COMMIT**: 호출부 MySQL 트랜잭션이 커밋된 뒤에만 실행되고, 롤백되면 아예 실행되지 않는다. `fallbackExecution = true`라 트랜잭션 없는 테스트 컨텍스트에서도 즉시 동작한다
- **이벤트에 내용을 싣지 않고 재조회한다**: 재시도로 처리 순서가 뒤섞일 때 오래된 값을 색인할 위험을 없앤다
- **`@EnableAsync`가 없으면** `@Async`가 조용히 무시되고 AFTER_COMMIT 콜백이 커밋한 스레드에서 그대로 동기 실행된다 — ES/OpenAI가 느리면 그 지연이 배치나 HTTP 요청 스레드를 물고 늘어진다
- 임베딩 텍스트는 "Index-time Search Context"로 보강한다 — 각 필드에 카테고리 계층 문자열을 앞에 붙여(`enrichedTitle = 카테고리 + 제목 + 요약`) 의미 밀도를 높인다. `categoryVector`용 계층 문자열은 부모 1단계가 아니라 **루트까지 조상 전체**를 붙인다(#765) — 그래야 리프 프로젝트가 상위 카테고리 의미를 잃지 않고, query-side `CategoryIntentResolver`와 같은 표현이라 벡터가 맞물린다

리워드가 등록·수정(이름 변경)·삭제되면 Reward 도메인이 `ProjectService.reindex(projectId)`를 호출한다 — ES의 `rewardNames`는 색인 시점에 리워드를 다시 조회해 채우므로, 이름이 바뀌면 프로젝트를 재색인해야 검색에 반영된다.

> ⚠️ **카테고리 개명에는 이 재색인 트리거가 없다.** `categoryId`는 그대로라 term 매치는 어긋나지 않지만, 5개 벡터는 전부 `CategoryHierarchy.path(categoryId)`가 만든 **카테고리 이름 문자열**을 앞에 붙여 임베딩한 것이라(`enrichedTitle = 카테고리 + 제목 + 요약` 등) 개명 후에는 옛 이름 기준 벡터가 남는다. 전체 재색인(`POST /api/v1/projects/reindex`) 전까지 벡터 검색 품질이 미세하게 어긋난다 — §10 참고.

### 5.6 실패 및 보상 흐름

```
마감 배치 실행
→ order-service pull
   → 성공: 마감 확정 → Kafka 발행 → settlement 정산/환불
   → 503 (서킷 열림/타임아웃): 이 프로젝트만 건너뜀 → 다음 배치가 재시도
→ 낙관적 락 충돌: 3회 재시도 → 소진 시 ConcurrentUpdateFailedException(409), failedProjectIds에 기록
```

- **fail-closed 원칙**: `OrderHttpClient`의 두 fallback은 기본값을 만들어내지 않고 `ServiceUnavailableException`을 던진다. "확인 안 됨"을 "후원 없음"으로 잘못 판단해 삭제를 허용하거나, pull 실패를 0원으로 덮어써 성공할 프로젝트를 실패로 확정하면 안 되기 때문이다
- **보상 처리**: 마감 확정에는 보상 트랜잭션이 없다. 대신 확정 자체가 멱등하지 않으므로(`closeByDeadline`은 `IN_PROGRESS`에서만 가능) 두 번 실행돼도 두 번째는 `IllegalStateException`으로 막힌다
- **중복/동시 요청**: 생성은 `(creatorId, idempotencyKey)` 유일 제약, 모금액 갱신은 절대값 덮어쓰기라 본질적으로 멱등, 상태 전이는 `requireStatus` 가드가 중복 적용을 막는다

## 6. 데이터 저장 구조

```
ProjectServiceImpl
→ ProjectRepository (JpaRepository + JpaSpecificationExecutor)
→ Hibernate / MySQL → projects
                 ↘
→ ProjectSearchPort → ProjectSearchAdapter → Elasticsearch → projects 인덱스
```

| 항목 | 내용 |
| --- | --- |
| 테이블 | `projects` (단일 테이블. Reward/Category는 각자 테이블) |
| 유니크 | `uk_projects_creator_id_idempotency_key (creator_id, idempotency_key)` |
| 외래 키 | **없다.** `category_id`, `creator_id`, `thumbnail_id` 모두 값 참조 |
| 체크 제약 | 없다. 금액·기간 검증은 전부 엔티티 |
| 동시성 제어 | `@Version` 낙관적 락 + `@Retryable`. 삭제 경로만 `PESSIMISTIC_WRITE`, 상태 조회는 `PESSIMISTIC_READ` |
| 같은 트랜잭션에서 처리해야 하는 데이터 | 상태 변경 + 리워드 비활성화 + 이벤트 등록 / 프로젝트 삭제 + 리워드 삭제 |

`ProjectRepository`의 락 메서드 두 개가 이 도메인의 동시성 설계를 요약한다.

| 메서드 | 락 | 용도 |
| --- | --- | --- |
| `findByIdForStatusCheck` | `PESSIMISTIC_READ` (LOCK IN SHARE MODE) | REPEATABLE READ 스냅샷을 우회해 항상 최신 커밋 상태를 읽고, 조회 중 다른 트랜잭션의 상태 변경을 대기시킨다. `ProjectStatusView` 전용 |
| `findByIdForDelete` | `PESSIMISTIC_WRITE` (FOR UPDATE) | 삭제 시 Project를 맨 먼저 선점해 락 순서 역전 데드락을 원천 차단 |

임베딩은 `projects.embedding` 컬럼(`LONGTEXT`)에 JSON 문자열로 저장된다(`EmbeddingConverter`, `@Converter(autoApply = true)`). MySQL에 벡터 타입이 없어서이며, 실제 kNN 검색은 ES의 `dense_vector`가 담당한다.

`ProjectEmbeddingPersister`는 임베딩 저장 전용 트랜잭션 경계다. 느린 외부 호출(OpenAI)이 끝난 뒤 결과값만 들고 `projectId`로 **다시 조회한 managed 엔티티**를 수정해 dirty-checking으로 저장한다 — detached 엔티티를 `save()`로 merge하면 그 사이 다른 트랜잭션이 바꾼 필드를 오래된 스냅샷으로 덮어쓸 위험이 있다. 벌크 재색인은 `findAllById`로 묶어 "페이지당 트랜잭션 1번"만 열리게 한다.

ES 인덱스는 `ProjectSearchIndexInitializer`가 기동 시 없으면 만든다 — 커스텀 분석기(nori)와 `dense_vector`는 **인덱스 생성 시점에만** 지정할 수 있어서, 문서를 먼저 저장해 자동 생성되게 두면 원하는 설정 없이 만들어진다. ES가 기동 시점에 안 떠 있어도 앱 부팅을 막지는 않는다.

## 7. 도메인 이벤트와 서비스 간 통신

```
도메인 상태 변경 (마감 확정 / 삭제)
→ ApplicationEventPublisher (내부 이벤트, 트랜잭션 안에서 등록)
→ [MySQL 커밋]
→ @TransactionalEventListener(AFTER_COMMIT) + @Async
→ KafkaTemplate.send(...).get(5s)
→ Kafka 토픽
→ 소비 서비스
```

| 이벤트 또는 토픽 | 발행 조건 | 소비 대상 | 전달 내용 |
| --- | --- | --- | --- |
| `project.status-changed.v1` | 마감 확정(`closeByDeadline`), 조기 마감, 취소 — `ProjectClosedEvent` → `ProjectClosedEventListener` | settlement-service | `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `payload{projectId, projectName, creatorId, status}` |
| `project.deleted.v1` | 프로젝트 삭제 커밋 후 — `ProjectFilesDeletionRequestedEvent` → 리스너 → `KafkaFileEventPublisher` | file-service | `payload{projectId}` |

두 토픽 모두 `common`의 `KafkaTopics`에 상수로 정의돼 있고, 처리 실패 격리용 DLT(`*.DLT`)가 함께 정의돼 있다.

- **내부 이벤트에는 projectId만 싣는다.** 리스너가 AFTER_COMMIT 시점에 DB에서 다시 조회해 그 순간의 최신 `status`/`creatorId`/`title`로 Kafka 이벤트를 만든다 — 이벤트에 내용을 실어 보내면 재시도로 처리 순서가 뒤섞일 때 오래된 값을 보낼 위험이 있다
- **AFTER_COMMIT이 필수인 이유**: `closeProjectByDeadlineInternal`은 `@Retryable`이라, 커밋 전에 발행하면 **롤백된 시도까지 settlement로 새어나간다**
- **발행 실패 처리가 두 토픽에서 다르다.** `project.deleted.v1`은 best-effort — 실패해도 WARN 로그만 남기고 프로젝트 삭제는 그대로 진행한다(파일 정리는 나중에 복구 가능). `project.status-changed.v1`은 실패 시 `IllegalStateException`을 던진다 — 정산/환불 트리거라 조용히 잃으면 안 되기 때문이다. 단, **AFTER_COMMIT 이후라 이 예외가 프로젝트 마감 자체를 롤백시키지는 못한다**(§10 참고)
- **재시도**: 자체 재시도 로직은 없다. 5초 타임아웃의 블로킹 전송 한 번이다
- **중복 발행 가능성**: 배치가 같은 프로젝트를 두 번 마감할 수는 없지만(`requireStatus` 가드), 소비자 측 멱등성은 settlement-service의 책임이다
- **순서 보장**: 파티션 키로 `projectId`를 쓴다 — 같은 프로젝트의 이벤트는 같은 파티션에 들어가 순서가 보장된다. 프로젝트 간 순서는 보장하지 않는다(최종 일관성)
- ⚠️ **두 토픽의 역직렬화가 실제로 동작하는지 미검증이다.** 공용 `application.yml`의 producer는 맨 `JsonSerializer`라 `spring.json.add.type.headers`가 기본값(true)인데, project-service에는 이를 끄거나 alias로 바꿔줄 `spring.json.type.mapping` 설정이 없다 — `__TypeId__` 헤더에 project-service의 FQCN이 그대로 실려 나간다. 소비 측(settlement의 `type.mapping` alias, file-service의 `value.default.type`) 어느 쪽도 그 FQCN을 자기 클래스로 해석하지 못해 전량 DLT로 갈 가능성이 높다. order-service·payment-service는 **producer 쪽 설정에도 alias를 둬서** 이 짝을 맞추고 있는데 project-service만 빠져 있다. 마감·삭제가 드물어 이 경로가 실제로 돌아본 적이 없을 수 있다 — #764에 "선행 확인 필요"로 함께 적혀 있다
- **스키마 호환**: `ProjectStatusChangedEvent`는 settlement-service의 컨슈머와 **필드가 동일한 계약**이라 임의로 바꾸면 안 된다. `projectName`은 #417에서 추가됐는데, Jackson이 알 수 없는 필드를 무시하므로 settlement가 아직 안 읽어도 기존 소비는 깨지지 않는다

동기 통신(Feign)은 `OrderPort` 하나뿐이다. `OrderFeignClient`(선언적 호출)를 `OrderHttpClient`(Resilience4j `CircuitBreakerFactory` 래핑)가 감싸 `OrderPort`를 구현하는 구조로, 팀 표준 어댑터 패턴을 따른다.

## 8. 예외 처리와 장애 복구

| 장애 상황 | 판별 기준 | 처리 방식 | 최종 상태 |
| --- | --- | --- | --- |
| order-service 응답 없음 (삭제 경로) | 서킷 오픈 / 3초 타임아웃 | fallback이 `ServiceUnavailableException` — 삭제 차단 (fail-closed) | 503 |
| order-service 응답 없음 (모금액 pull) | 위와 동일 | `ServiceUnavailableException` → 호출부가 이 프로젝트만 건너뜀 | 다음 배치가 재시도 |
| ES 색인 실패 | 서킷/예외 | **로그만 남기고 삼킨다** — MySQL 트랜잭션·응답에 영향 없음 | 인덱스가 stale, `reindex`로 복구 |
| ES 검색 실패 | 서킷 오픈 / 10초 타임아웃 | DB `title LIKE '%kw%'` 폴백 (와일드카드 이스케이프) | 품질 저하, 화면은 동작 |
| ES 자동완성 실패 | 800ms 타임아웃 | 빈 목록 | 자동완성만 비활성 |
| OpenAI 임베딩 실패 | 15초 타임아웃 / 서킷 | 벡터 없이 진행 → BM25 단독 검색 | 품질 저하 |
| Cohere Rerank 실패 | 1.5초 타임아웃 / 서킷 | fusion 순서 유지 | 품질 저하 |
| 벌크 재색인 실패 | 180초 타임아웃 | 해당 페이지만 로그 후 계속 | 일부 미색인 |
| 낙관적 락 충돌 반복 | `@Retryable` 3회 소진 | `ConcurrentUpdateFailedException` | 409 |
| 잘못된 상태 전이 | `requireStatus` | `IllegalStateException` | 409 |
| 비공개 프로젝트를 권한 없이 조회 | `isPublished() == false` && 소유자·ADMIN 아님 | `EntityNotFoundException` | **404** (403이 아님 — 존재 자체를 숨긴다) |

서킷브레이커 설정 (`slidingWindowSize=10`, `minimumNumberOfCalls=4`, `failureRateThreshold=50%`, `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=2` 공통):

| id | 타임아웃 | 대상 |
| --- | --- | --- |
| `order` | 3s | order-service Feign |
| `projectSearch` | 10s | 하이브리드 검색 |
| `projectAutocomplete` | 800ms | 자동완성 |
| `projectEmbedding` | 15s | OpenAI 임베딩 |
| `projectBulkIndex` | 180s | 벌크 재색인 (페이지당 최대 50개 임베딩 일괄 생성) |
| `projectRerank` | 1.5s | Cohere Rerank |

> 타임아웃은 `Resilience4JConfigBuilder.timeLimiterConfig(...)`가 아니라 `TimeLimiterRegistry`에 **이름을 붙여 직접 등록**해야 실제로 적용된다. spring-cloud-circuitbreaker-resilience4j에서 전자는 무시되고 resilience4j 전역 기본값(1초)으로 떨어진다 — 바이트코드 추적과 실측으로 확인한 라이브러리 동작이다.

복구 수단:

- **모금액 정합성**: `FundedAmountReconciliationScheduler`가 1시간마다 `IN_PROGRESS` 프로젝트의 모금액을 pull로 재확인한다. push 유실에 대한 백스톱이다
- **마감 누락**: 배치가 매일 자정 재실행되므로, 실패한 프로젝트는 다음날 자동으로 다시 시도된다. 즉시 복구가 필요하면 관리자가 `POST /api/v1/projects/close-expired`로 수동 트리거한다
- **검색 인덱스 정합성**: 관리자가 `POST /api/v1/projects/reindex`로 전체 재색인한다. 50개씩 페이징하며, 한 페이지 실패가 전체를 중단시키지 않는다
- **확정 실패와 결과 불명의 구분**: 409(상태 전이 불가)와 400(검증 실패)은 확정 실패라 재시도해도 같다. 503은 결과 불명이라 재시도가 유효하다

## 9. 테스트 현황

| 테스트 | 검증 범위 |
| --- | --- |
| `ProjectTest` | 엔티티 규칙 — 금액/기간(최대 3개월) 검증, 상태 전이 가드, `isOpen`의 `endAt+1일` 경계, `updateBeforePublish`/`AfterPublish` 필드 제한 |
| `ProjectServiceImplOwnershipTest` | 소유권 검증, 비공개 프로젝트의 404 처리, ADMIN 우회 |
| `ProjectServiceImplCancelTest` | 취소 가능 상태(`IN_PROGRESS`/`SUCCEEDED`), 금지 상태 거부, ADMIN 취소 |
| `ProjectServiceImplDeleteTest` | 후원 이력 존재 시 거부, 리워드 동반 삭제, 파일 삭제 이벤트 발행 |
| `ProjectServiceImplReconciliationTest` | 모금액 pull 보정, 건별 실패 격리 |
| `ProjectServiceImplRetryTest` | 낙관적 락 재시도와 `@Recover`의 409 변환 |
| `ProjectServiceImplFindAllSearchTest` | 키워드/카테고리/상태/정렬 조합, 관련도 정렬 기본값 |
| `ProjectCategoryFilterIntegrationTest` | 상위 카테고리 필터가 하위 프로젝트를 잡는지 (#761) |
| `ProjectServiceImplAutocompleteTest` | 자동완성의 MySQL 재필터, 최대 10건 제한 |
| `ProjectServiceImplSearchIndexingTest` / `ReindexTest` | 색인 이벤트 발행, 리워드 변경 시 재색인, 벌크 재색인 페이징 |
| `ProjectServiceImplSearchIntegrationTest` | 검색 전체 파이프라인 (Testcontainers ES) |
| `ProjectConcurrencyIntegrationTest` / `ProjectDeleteConcurrencyIntegrationTest` | 동시 수정, 삭제와 재고 차감의 락 순서 (Testcontainers MySQL) |
| `ProjectSearchAdapterTest` / `ProjectSearchAdapterIntegrationTest` / `ProjectSearchConcurrencyTest` | fusion 가중치, 컷오프, 폴백, 병렬 실행 |
| `ProjectSearchGoldenSetEvaluationTest` / `SearchScoreVerificationTest` | 골든셋 기반 검색 품질, 점수 분포 검증 |
| `CohereRerankerTest` / `CohereRerankClientTest` / `CohereRerankerRealApiTest` / `NoOpRerankerTest` | 관련도 컷 규칙, 1등 보존, 실패 시 fusion 순서 유지 |
| `CategoryIntentResolverTest` / `QuerySynonymExpanderTest` / `SeasonalConflictFilterTest` | 의도 추론 임계값, 동의어 확장, 계절 충돌 하드 제외 |
| `ProjectEmbeddingServiceTest` | 5필드 배치 임베딩, 실패 시 빈 벡터 |
| `ProjectSearchIndexBootstrapTest` | 기동 시 인덱스 생성 |
| `ProjectSearchCircuitBreakerConfigTest` / `OrderCircuitBreakerConfigTest` | `TimeLimiterRegistry` 등록이 실제로 적용되는지 |
| `OrderHttpClientTest` | fail-closed fallback (503) |
| `KafkaFileEventPublisherTest` / `ProjectFilesDeletionRequestedEventListenerTest` | best-effort 발행, AFTER_COMMIT 시점 |
| `ProjectControllerTest` / `EnvelopeTest` / `KeywordValidationTest` / `ProjectInternalControllerTest` | 권한, 응답 envelope, 키워드 길이 제한, 내부 API 계약 |

테스트하지 못한 중요 시나리오:

- **CI는 벡터 검색을 검증하지 않는다.** `cohere.rerank.enabled=false` + OpenAI 키 없음 환경이라 BM25 경로만 돈다. 벡터/리랭킹 품질 회귀는 CI가 잡지 못한다
- **로컬에서 ES 통합 테스트가 Docker 메모리(8GB) 제약으로 OOM(exit 137)이 난다** — 스택을 전부 띄운 상태에서 검색 통합 테스트 5개가 죽는다. CI에서는 통과한다
- **Kafka 발행 실패 후의 정합성 복구** — settlement가 마감 이벤트를 못 받은 경우를 재현·복구하는 테스트가 없다
- 다중 인스턴스에서 마감 배치가 동시에 도는 경우 (현재는 단일 인스턴스 전제)

## 10. 현재 한계와 후속 과제

- **마감 배치가 다중 인스턴스 안전하지 않다.** `@Scheduled`가 인스턴스마다 돌면 같은 프로젝트를 동시에 마감하려 시도한다. `requireStatus` 가드와 낙관적 락이 이중 확정은 막지만, 불필요한 order-service 호출과 409가 발생한다.
    - 대응 계획: ShedLock 또는 settlement처럼 Spring Batch로 이관. 스케일아웃 시점에 필수.
- **`project.status-changed.v1` 발행 실패가 마감을 되돌리지 못한다.** AFTER_COMMIT 이후라 예외를 던져도 이미 커밋된 상태 변경은 남는다 — 프로젝트는 `SUCCEEDED`인데 settlement는 모르는 상태가 될 수 있다.
    - 대응 계획: Outbox 패턴(상태 변경과 이벤트를 같은 트랜잭션에 기록 → 별도 릴레이가 발행). 현재는 DLT와 수동 재발행에 의존한다.
- **CI가 벡터 검색·리랭킹을 검증하지 않는다.** 키워드 경로만 도는 환경이라 검색 품질 회귀가 배포 후에야 드러난다.
    - 대응 계획: 골든셋 평가를 별도 워크플로로 분리해 키가 있는 환경에서 주기 실행. 별도 이슈로 추적 중.
- **`SeasonalConflictFilter`는 리랭커 도입 후 역할이 겹친다.** 정적 키워드 목록이라 유지보수 비용이 있고, 대부분의 판단은 이미 Cohere가 한다.
    - 대응 계획: 리랭킹 품질이 안정화되면 제거를 검토 (검색 후속 정리 항목).
- **`CategoryIntentResolver`의 카테고리 벡터 캐시가 무효화 경로 없이 `ConcurrentHashMap`에 영구 보관된다.** 카테고리가 추가·개명돼도 재기동 전까지 반영되지 않는다.
    - 대응 계획: TTL 또는 카테고리 변경 시 무효화 훅 추가.
- **`CategoryHierarchy`가 검색 요청마다 `findAll()`을 호출한다.** 카테고리가 커지면 요청당 반복 조회 비용이 눈에 띈다.
    - 대응 계획: 위 캐시 개선과 함께 계층 스냅샷 캐싱.
- **`SUCCEEDED → CANCELLED`의 "정산 전까지만"이 코드로 강제되지 않는다.** `Project.cancel()`은 `status`만 보고, project-service는 정산 진행 여부를 조회하지 않는다 — 이미 창작자에게 지급이 끝난 프로젝트도 지금은 취소되고, settlement가 그 이벤트를 받아 환불 대상으로 다루게 된다.
    - 대응 계획: settlement에 정산 상태 조회 API가 생기면 `cancel()` 전에 확인하거나, settlement 쪽에서 "이미 정산된 프로젝트의 환불 요청"을 거부하도록 한다. 어느 쪽이든 두 서비스 합의가 필요해 project-service 단독으로 결정하지 않는다.
- **카테고리 개명 시 소속 프로젝트의 임베딩 벡터가 stale해진다.** 5개 벡터가 전부 카테고리 이름 문자열을 앞에 붙여 임베딩한 것이라, 개명 후에도 옛 이름 기준 벡터가 남는다. `categoryId` term 매치는 영향이 없어 증상이 조용하다(§5.5).
    - 대응 계획: `ProjectCategoryServiceImpl.updateTransactional`에서 이름이 바뀐 경우 소속 프로젝트를 재색인한다. `ProjectRepository.findByCategoryId`가 바로 그 용도로 추가돼 있으나(javadoc이 그렇게 적혀 있다) **현재 호출하는 곳이 없는 죽은 메서드**다 — 이 배선을 다시 잇거나, 잇지 않을 거면 메서드와 javadoc을 삭제해야 한다.
- **`OrderCircuitBreakerConfig`가 `"file"` TimeLimiter 설정을 등록하지만 이를 쓰는 서킷브레이커가 없다.** `FilePort`가 HTTP에서 Kafka로 바뀌면서 남은 흔적이다.
    - 대응 계획: 단순 삭제. 동작에 영향은 없다.
- **FK가 없어 `categoryId`/`creatorId`/`thumbnailId`의 참조무결성이 애플리케이션 검증에만 의존한다.**
    - 대응 계획: Project·Reward의 별도 서비스 분리 계획을 고려하면 FK를 거는 것이 맞지 않는다. 대신 참조 깨짐을 감지하는 정합성 점검을 검토.
- **`idempotencyKey`가 선택 필드다.** 미전송 시 서버가 랜덤 UUID를 만들어 중복 방지가 적용되지 않는다.
    - 대응 계획: 프론트엔드 전 경로 적용 확인 후 `@NotNull` 승격 (Reward와 동일).
- **로컬 개발 환경에서 ES 통합 테스트가 Docker 메모리 부족으로 실패한다.**
    - 대응 계획: Docker 메모리 상향 안내를 개발 문서에 명시하거나, 통합 테스트를 별도 Gradle task로 분리.
