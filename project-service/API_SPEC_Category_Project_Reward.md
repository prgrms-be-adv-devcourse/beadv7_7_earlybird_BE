# project-service API 명세서

`project-service` (:8081) — Category / Project / Reward 도메인. 외부 요청은 게이트웨이(:8000)를 거쳐 `/api/v1/**` 경로로 들어오고, `/internal/v1/**`는 게이트웨이 라우팅이 없어 서비스 간 직접 호출(Eureka)만 가능하다.

공통 응답 포맷은 `ApiResponse<T>`로 컨트롤러가 감싸지 않아도 `ApiResponseWrappingAdvice`가 자동으로 감싼다.

```json
// 성공
{ "success": true, "data": { ... }, "error": null }

// 실패
{
  "success": false,
  "data": null,
  "error": { "code": "C001", "message": "잘못된 요청입니다.", "errors": [ { "field": "title", "message": "must not be blank" } ] }
}
```

## 공통 에러 코드

| code | HTTP status | 의미 |
| --- | --- | --- |
| `C001` | 400 Bad Request | 잘못된 요청 (검증 실패, 잘못된 상태 전이 요청 등) |
| `C002` | 409 Conflict | 처리할 수 없는 상태 (예: 재고 부족, 잘못된 상태 전이) |
| `C003` | 404 Not Found | 대상 리소스 없음 |
| `C503` | 503 Service Unavailable | 연동 서비스(order-service 등) 일시 장애 |
| `C500` | 500 Internal Server Error | 서버 내부 오류 |

> **인증/인가 적용 완료 (2026-07-23 기준)**: JWT 인증이 도입되어 게이트웨이가 검증 후 `X-User-Id`/`X-User-Role` 헤더로 값을 전달한다. `creatorId`/`requesterId`는 더 이상 요청 body가 아니라 이 헤더에서 받으며, 프로젝트/리워드의 수정·삭제는 본인(소유자) 또는 ADMIN만 가능하도록 서비스 레벨에서 검증한다(게이트웨이는 role 기반 라우팅만 하고, "본인 소유인지"는 각 서비스가 판단). 관리자 API는 URL에 role을 노출하지 않는다는 팀 원칙에 따라 `/admin` 프리픽스 없이 일반 리소스 경로 아래 있고, `X-User-Role: ADMIN`이 아니면 `400`으로 거부된다.

---

## 1. Project API

Base path: `/api/v1/projects`

### 1.1 프로젝트 등록
`POST /api/v1/projects`

**Headers**: `X-User-Id`(Long, 필수), `X-User-Role`(필수, `CREATOR` 또는 `ADMIN`만 허용 — `BACKER`는 `400`)

**Request Body**

| field | type | required | 설명 |
| --- | --- | --- | --- |
| thumbnailId | Long | | 썸네일 파일 ID |
| title | String | ✓ | 제목 |
| categoryId | Long | ✓ | 카테고리 ID |
| summary | String | | 한줄 요약 |
| description | String | | 상세 설명 |
| goalAmount | BigDecimal | ✓ | 목표 금액 |
| startAt | LocalDateTime | ✓ | 펀딩 시작 일시 |
| endAt | LocalDate | ✓ | 펀딩 마감일 (해당일 포함, 다음날 00:00에 자동 마감) |

**Response**: `ProjectResponse` (아래 1.4 참고), 초기 상태 `PENDING_REVIEW`

### 1.2 프로젝트 목록 조회
`GET /api/v1/projects`

**Headers**: `X-User-Role`(필수) — `ADMIN`이면 `PENDING_REVIEW`/`REJECTED` 상태도 결과에 포함된다.

**Query Parameters** (모두 선택)

| param | type | 설명 |
| --- | --- | --- |
| keyword | String | 제목/요약 검색어 |
| categoryId | Long | 카테고리 필터 |
| status | ProjectStatus | 상태 필터 (아래 enum 참고) |
| sort | ProjectSort | `LATEST`(최신순, 기본) / `DEADLINE`(마감임박순) / `FUNDED_AMOUNT`(펀딩액순) |

**Response**: `List<ProjectResponse>`

### 1.3 내 프로젝트 목록
`GET /api/v1/projects/me`

**Headers**: `X-User-Id` (Long, 필수)

**Response**: `List<ProjectResponse>`

### 1.4 프로젝트 단건 조회
`GET /api/v1/projects/{projectId}`

**Response**: `ProjectResponse`

```json
{
  "projectId": 1,
  "creatorId": 10,
  "thumbnailId": 3,
  "title": "string",
  "categoryId": 2,
  "summary": "string",
  "description": "string",
  "goalAmount": 1000000,
  "fundedAmount": 250000,
  "startAt": "2026-08-01T00:00:00",
  "endAt": "2026-09-01",
  "status": "IN_PROGRESS",
  "closed": false,
  "rejectReason": null,
  "submittedAt": "2026-07-20T10:00:00",
  "approvedAt": "2026-07-21T09:00:00",
  "closedAt": null,
  "createdAt": "2026-07-20T10:00:00",
  "updatedAt": "2026-07-21T09:00:00"
}
```

### 1.5 프로젝트 수정
`PATCH /api/v1/projects/{projectId}`

**Headers**: `X-User-Id`(필수) — 본인이 등록한 프로젝트가 아니면 `400`.

**Request Body** (모든 필드 선택, `null`은 미변경)

| field | type | 공개 전 | 공개 후 |
| --- | --- | --- | --- |
| title | String | 가능 | 불가 (400) |
| categoryId | Long | 가능 | 불가 (400) |
| summary | String | 가능 | 가능 |
| description | String | 가능 | 가능 |
| thumbnailId | Long | 가능 | 가능 |
| goalAmount | BigDecimal | 가능 | 불가 (400) |
| startAt | LocalDateTime | 가능 | 불가 (400) |
| endAt | LocalDate | 가능 | 불가 (400, 창작자는 마감일 변경 불가 — 관리자 전용 API 참고 3.3) |

**Response**: `ProjectResponse`

### 1.6 프로젝트 삭제
`DELETE /api/v1/projects/{projectId}`

**Headers**: `X-User-Id`(필수) — 본인이 등록한 프로젝트가 아니면 `400`.

본인 확인을 통과해도, 주문(후원) 내역이 하나라도 있으면 삭제 불가(`C002`, order-service 조회 결과 fail-closed). 연관된 모든 리워드도 함께 삭제된다.

**Response**: `204` 상당의 빈 `data: null`

### 1.7 프로젝트 자진 취소
`POST /api/v1/projects/{projectId}/cancel`

**Headers**: `X-User-Id`(필수), `X-User-Role`(필수) — 본인(창작자) 또는 ADMIN만 가능, 아니면 `400`.

진행중(`IN_PROGRESS`) 또는 이미 목표 달성(`SUCCEEDED`)한 상태에서만 가능 — 이미 `FAILED`거나 이미 `CANCELLED`는 대상 아님(실패는 이미 자동 환불 파이프라인을 타므로 취소가 의미 없음). 목표 달성 후 취소를 허용하는 이유는 "목표는 달성했지만 창작자가 배송 등을 감당 못 하게 된" 경우를 위함. 취소되면 다른 종료 케이스와 마찬가지로 리워드도 함께 비활성화된다. 환불 대상(`CANCELLED`/`FAILED`) 프로젝트는 Settlement가 §5.3 내부 API로 조회해 일괄 환불한다(Payment는 orderId 기준 단건 PG 취소·환불만 담당).

**Response**: `ProjectResponse`

### Project 상태 (`ProjectStatus`)

| value | 의미 |
| --- | --- |
| `PENDING_REVIEW` | 심사 대기 |
| `REJECTED` | 심사 반려 |
| `IN_PROGRESS` | 심사 승인, 펀딩 진행중 |
| `SUCCEEDED` | 목표 금액 달성(마감) |
| `FAILED` | 목표 금액 미달(마감) |
| `CANCELLED` | 창작자/관리자에 의한 중단(마감) |

`SUCCEEDED`/`FAILED`/`CANCELLED`는 `closed = true`.

---

## 2. Reward API

Base path: `/api/v1` (프로젝트 하위 리소스 + 단건 리소스 혼용)

### 2.1 리워드 등록
`POST /api/v1/projects/{projectId}/rewards`

**Headers**: `X-User-Id`(필수) — 리워드는 자기 creatorId가 없어 부모 프로젝트의 creatorId로 소유권을 검증한다. 본인(그 프로젝트의 창작자)이 아니면 `400`.

**Request Body**

| field | type | required | 설명 |
| --- | --- | --- | --- |
| name | String | ✓ | 리워드명 |
| description | String | | 설명 |
| price | BigDecimal | ✓ | 가격 |
| totalQuantity | Integer | (≥0) | 비워두면 무제한 리워드 |

**Response**: `RewardResponse`

### 2.2 프로젝트별 리워드 목록
`GET /api/v1/projects/{projectId}/rewards`

**Response**: `List<RewardResponse>`

### 2.3 리워드 단건 조회
`GET /api/v1/rewards/{rewardId}`

**Response**: `RewardResponse`

```json
{
  "rewardId": 1,
  "projectId": 1,
  "name": "string",
  "description": "string",
  "price": 10000,
  "totalQuantity": 100,
  "remainingQuantity": 87,
  "orderable": true,
  "active": true
}
```

### 2.4 리워드 수정
`PATCH /api/v1/rewards/{rewardId}`

**Headers**: `X-User-Id`(필수) — 부모 프로젝트의 창작자 본인이 아니면 `400`.

**Request Body**

| field | type | 공개 전 | 공개 후 |
| --- | --- | --- | --- |
| name | String | 가능 | 불가 |
| description | String | 가능 | 불가 |
| price | BigDecimal | 가능 | 불가 |
| totalQuantity | Integer (≥0) | 가능 | 불가 |
| increaseQuantity | Integer (>0) | — | 가능 (재고 증가만) |

공개 후에는 `increaseQuantity` 외 필드를 함께 보내면 `400`.

**Response**: `RewardResponse`

### 2.5 리워드 삭제
`DELETE /api/v1/rewards/{rewardId}`

**Headers**: `X-User-Id`(필수) — 부모 프로젝트의 창작자 본인이 아니면 `400`.

공개 전: 하드 삭제. 공개 후: 비활성화(`active=false`, 판매 종료)만 수행하고 레코드는 보존.

**Response**: 빈 `data: null`

---

## 3. 관리자 API

별도 base path 없음 — 팀 원칙("URL에 role을 노출하지 않는다")에 따라 관리자 전용 엔드포인트도 일반 리소스 경로(`/api/v1/projects/...`, `/api/v1/rewards/...`) 아래에 그대로 있고, `X-User-Role: ADMIN`이 아니면 `400`으로 거부된다. (예전엔 `ProjectAdminController`/`RewardAdminController`로 컨트롤러 자체와 `/admin/...` 경로를 분리했었지만, 팀 원칙과 어긋나 일반 컨트롤러로 합쳤다 — 상태별 프로젝트 조회는 그래서 별도 엔드포인트가 아니라 §1.2에 `ADMIN` 분기로 통합돼 있다.)

> 모든 항목 공통: `X-User-Role`(필수) — `ADMIN`이 아니면 `400`.

### 3.1 프로젝트 심사 승인
`POST /api/v1/projects/{projectId}/approve`

`PENDING_REVIEW` → `IN_PROGRESS`. 다른 상태에서 호출 시 `409 (C002)`.

**Response**: `ProjectResponse`

### 3.2 프로젝트 심사 반려
`POST /api/v1/projects/{projectId}/reject`

**Request Body**: `{ "reason": "string" }` (필수)

`PENDING_REVIEW` → `REJECTED`.

**Response**: `ProjectResponse`

### 3.3 마감일 연장
`PATCH /api/v1/projects/{projectId}/deadline`

**Request Body**: `{ "endAt": "2026-10-01" }` (필수, 기존 값보다 뒤로만 가능)

**Response**: `ProjectResponse`

### 3.4 마감 프로젝트 일괄 정산 즉시 실행
`POST /api/v1/projects/close-expired`

마감일이 지난 `IN_PROGRESS` 프로젝트를 목표 달성 여부에 따라 `SUCCEEDED`/`FAILED`로 일괄 확정한다. 매일 자정(Asia/Seoul) 스케줄러가 자동 실행하며, 이 API는 수동/즉시 트리거용.

**Response**: 빈 `data: null`

### 3.5 조기 종료
`POST /api/v1/projects/{projectId}/close-early`

목표 금액을 이미 달성한 `IN_PROGRESS` 프로젝트를 마감일 전에 `SUCCEEDED`로 조기 확정.

**Response**: `ProjectResponse`

### 3.6 리워드 재고 축소
`PATCH /api/v1/rewards/{rewardId}/quantity`

**Request Body**: `{ "amount": 10 }` (필수, 양수) — 이미 판매된 수량 밑으로는 축소 불가(`409`).

**Response**: `RewardResponse`

### 3.7 리워드 비활성화
`POST /api/v1/rewards/{rewardId}/deactivate`

공개(진행중) 리워드를 강제 비활성화(`active=false`, 레코드는 보존). 크리에이터는 이 권한 없음 — §2.5 리워드 삭제와 달리 관리자 전용이다.

**Response**: 빈 `data: null`

---

## 4. Category API

Base path: `/api/v1/project-categories`

### 4.1 카테고리 생성
`POST /api/v1/project-categories`

**Request Body**

| field | type | required | 설명 |
| --- | --- | --- | --- |
| parentProjectCategoryId | Long | | 상위 카테고리 ID (없으면 루트) |
| name | String | ✓ | 카테고리명 |

**Response**: `ProjectCategoryResponse`

### 4.2 카테고리 트리 조회
`GET /api/v1/project-categories`

전체 카테고리를 트리 구조로 반환.

**Response**: `List<ProjectCategoryResponse>`

```json
[
  {
    "id": 1,
    "parentProjectCategoryId": null,
    "name": "테크",
    "children": [
      { "id": 2, "parentProjectCategoryId": 1, "name": "가전", "children": [] }
    ]
  }
]
```

### 4.3 카테고리 단건 조회
`GET /api/v1/project-categories/{projectCategoryId}`

**Response**: `ProjectCategoryResponse` — **`children`은 실제 자식 유무와 무관하게 항상 빈 배열이다** (트리 조립은 §4.2 목록 조회만 수행하고, 단건 조회는 자식을 채우지 않는다 — 자식이 있는 카테고리를 단건 조회해도 `children: []`로 온다는 뜻이니 주의).

### 4.4 카테고리 수정
`PUT /api/v1/project-categories/{projectCategoryId}`

**Request Body**: `{ "parentProjectCategoryId": Long|null, "name": "string" }` (name 필수)

자기 자신 또는 자손을 부모로 지정하면 거부(`400`).

**Response**: `ProjectCategoryResponse`

> 카테고리 DELETE는 아직 없음 — 하위 참조(프로젝트/자식 카테고리) 처리 정책이 미정이라 보류 중.

---

## 5. 내부 API (서비스 간 전용, 게이트웨이 미경유)

Base path: `/internal/v1` — Eureka를 통한 서비스 간 직접 호출만 가능, 외부 클라이언트는 접근 불가.

### 5.1 리워드 재고 차감
`POST /internal/v1/rewards/{rewardId}/decrease-stock`

order-service가 주문(후원) 생성 시 호출.

**Request Body**: `{ "quantity": 1 }` (필수, 양수)

**Response**: 빈 `data: null`. 재고 부족 시 `409 (C002)`.

### 5.2 리워드 재고 복원
`POST /internal/v1/rewards/{rewardId}/restore-stock`

order-service가 주문 취소/실패(SAGA 보상) 시 호출.

**Request Body**: `{ "quantity": 1 }` (필수, 양수)

**Response**: 빈 `data: null`

### 5.3 상태별 프로젝트 조회 (내부용)
`GET /internal/v1/projects?status={ProjectStatus}`

Settlement가 정산 대상(`SUCCEEDED`)과 환불 대상(`FAILED`/`CANCELLED`) 프로젝트 목록을 조회할 때 호출한다. Payment는 프로젝트 상태 조회 없이 orderId 기준 단건 PG 취소·환불만 담당하므로 이 API를 호출하지 않는다. `GET /api/v1/projects?status=X`(§1.2)와 기능은 같지만, 그건 사람(ADMIN JWT) 전용이라 서비스 간 호출에는 이 경로를 따로 둔다.

**Response**: `List<ProjectResponse>`

---

## 부록: 엔드포인트 요약

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/v1/projects` | 프로젝트 등록 | X-User-Id + X-User-Role(CREATOR/ADMIN) |
| GET | `/api/v1/projects` | 목록 조회 (ADMIN이면 심사대기/반려도 포함) | X-User-Role |
| GET | `/api/v1/projects/me` | 내 프로젝트 목록 | X-User-Id |
| GET | `/api/v1/projects/{id}` | 단건 조회 | - |
| PATCH | `/api/v1/projects/{id}` | 수정 (본인만) | X-User-Id |
| DELETE | `/api/v1/projects/{id}` | 삭제 (본인만, 주문 있으면 거부) | X-User-Id |
| POST | `/api/v1/projects/{id}/cancel` | 자진 취소 | X-User-Id + X-User-Role(본인/ADMIN) |
| POST | `/api/v1/projects/{id}/approve` | 심사 승인 | X-User-Role(ADMIN) |
| POST | `/api/v1/projects/{id}/reject` | 심사 반려 | X-User-Role(ADMIN) |
| PATCH | `/api/v1/projects/{id}/deadline` | 마감일 연장 | X-User-Role(ADMIN) |
| POST | `/api/v1/projects/close-expired` | 마감 일괄 정산 즉시 실행 | X-User-Role(ADMIN) |
| POST | `/api/v1/projects/{id}/close-early` | 조기 종료 | X-User-Role(ADMIN) |
| POST | `/api/v1/projects/{id}/rewards` | 리워드 등록 (부모 프로젝트 본인만) | X-User-Id |
| GET | `/api/v1/projects/{id}/rewards` | 리워드 목록 | - |
| GET | `/api/v1/rewards/{id}` | 리워드 단건 조회 | - |
| PATCH | `/api/v1/rewards/{id}` | 리워드 수정 (부모 프로젝트 본인만) | X-User-Id |
| DELETE | `/api/v1/rewards/{id}` | 리워드 삭제(공개 전)/비활성화(공개 후) (부모 프로젝트 본인만) | X-User-Id |
| PATCH | `/api/v1/rewards/{id}/quantity` | 리워드 재고 축소 | X-User-Role(ADMIN) |
| POST | `/api/v1/rewards/{id}/deactivate` | 리워드 비활성화 (관리자 강제) | X-User-Role(ADMIN) |
| POST | `/api/v1/project-categories` | 카테고리 생성 | - |
| GET | `/api/v1/project-categories` | 카테고리 트리 조회 | - |
| GET | `/api/v1/project-categories/{id}` | 카테고리 단건 조회 (children 항상 빈 배열) | - |
| PUT | `/api/v1/project-categories/{id}` | 카테고리 수정 | - |
| POST | `/internal/v1/rewards/{id}/decrease-stock` | 재고 차감 (내부) | 서비스 간 전용 |
| POST | `/internal/v1/rewards/{id}/restore-stock` | 재고 복원 (내부) | 서비스 간 전용 |
| GET | `/internal/v1/projects?status=X` | 상태별 프로젝트 조회 (내부, Settlement 전용) | 서비스 간 전용 |
