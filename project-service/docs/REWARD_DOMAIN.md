# Reward 도메인

담당자: 강대혁
기준일: 2026-08-31

이 문서는 목표 설계가 아니라 현재 `project-service` 구현을 기준으로 Reward(후원 옵션) 도메인의 책임과 처리 흐름을 설명한다.

## 0. 목차

1. 도메인 개요
2. 도메인 모델
3. 상태 전이
4. API
5. 주요 처리 흐름
6. 데이터 저장 구조
7. 예외 처리와 장애 복구
8. 테스트 현황
9. 현재 한계와 후속 과제

> 이 도메인이 직접 발행하는 Kafka 이벤트는 없어 "도메인 이벤트" 장은 두지 않는다. 서비스 간 통신은 §4.2 내부 API로 이뤄진다.

## 1. 도메인 개요

### 1.1 책임 범위

Reward는 **펀딩 액수별 후원 옵션(상품)**이다. 프로젝트가 "얼마를 모으는가"를 담당한다면 Reward는 "얼마를 내면 무엇을 받는가"를 담당한다. 가격과 재고는 프로젝트가 아니라 전부 Reward가 가진다.

- 리워드의 등록·수정·삭제 (프로젝트 창작자) 및 수량 축소·비활성화 (관리자)
- 한정 수량(얼리버드) 리워드의 **재고 정합성** — 초과 판매가 절대 발생하지 않도록 차감/복원을 원자적으로 처리
- order-service가 보내는 재고 변경 요청의 **멱등성** — 같은 주문의 중복 요청이 재고를 두 번 깎지 않도록 보장
- 판매 가능 여부(`orderable`) 계산 — 활성 상태이고 재고가 남았는지

이 도메인이 담당하지 않는 범위:

| 범위 | 담당 |
| --- | --- |
| 프로젝트의 공개 여부·마감 상태 판단 | Project 도메인 (`ProjectStatusView`로 조회) |
| 주문 생성/취소, 결제 | order-service / payment-service |
| 프로젝트 모금액(`fundedAmount`) 누적 | Project 도메인 (order-service push + pull 보정) |
| 검색 색인 | Project 도메인의 검색 인프라 (리워드 이름 변경 시 `reindex` 요청만 함) |

Reward는 **자기 재고와 활성 상태만 안다.** 부모 프로젝트가 진행중인지는 스스로 판단하지 않고, 애플리케이션 서비스가 `ProjectService.findStatusView()`로 물어본다.

### 1.2 다른 서비스와의 관계

| 연관 대상 | 통신 방향 | 주고받는 정보 | Reward의 책임 | 상대 대상의 책임 |
| --- | --- | --- | --- | --- |
| order-service | order → project-service | `POST /internal/v1/rewards/{id}/decrease-stock`, `/restore-stock` (`quantity`, `orderId`) | 재고를 원자적으로 차감/복원하고 중복 요청을 멱등 처리 | 주문 생성 시 차감 요청, 취소·환불 시 복원 요청. `orderId`를 반드시 실어 보냄 |
| cart-service | cart → project-service | `GET /api/v1/rewards/{rewardId}` | 리워드 단건 정보(가격, 재고, orderable) 응답 | 장바구니 담기/조회 시 리워드 유효성 확인 |
| chat-service | chat → project-service | `GET /api/v1/projects/{id}/rewards`, `GET /api/v1/rewards/{id}` | 리워드 목록/단건 응답 | AI 챗봇이 리워드를 안내할 때 조회 |
| Project 도메인 (같은 서비스) | Reward → Project | `findStatusView(projectId)` | 공개/마감/진행중 여부와 창작자 id를 요청 | 공유 락으로 최신 커밋 상태를 뷰로 반환 |
| Project 도메인 (같은 서비스) | Project → Reward | `deactivateAllByProject`, `deleteAllByProject` | 프로젝트 마감/삭제에 맞춰 리워드 일괄 처리 | 마감/삭제 트랜잭션에서 호출 |

```
order-service → POST /internal/v1/rewards/{id}/decrease-stock  : 주문 확정 시 재고 차감
order-service → POST /internal/v1/rewards/{id}/restore-stock   : 주문 취소·환불 시 재고 복원 (SAGA 보상)
cart/chat     → GET  /api/v1/rewards/{id}                       : 리워드 조회
Project 마감  → RewardService.deactivateAllByProject            : 마감 프로젝트의 리워드 판매 종료
Project 삭제  → RewardService.deleteAllByProject                : 공개 전 삭제 시 리워드 하드 삭제
```

책임 경계: **초과 판매 방지의 최종 책임은 Reward에 있다.** order-service가 사전에 재고를 확인했더라도 Reward는 다시 검증하며, 실제 방어선은 애플리케이션 조건문이 아니라 DB의 조건부 `UPDATE` WHERE 절이다(§5.1). 반대로 "이 주문이 유효한 주문인가"는 Reward가 검증하지 않고 order-service를 신뢰한다 — `/internal/v1`은 게이트웨이를 거치지 않는 내부망 전용 경로이기 때문이다.

`restoreStock`은 이 프로젝트에서 **SAGA 보상 트랜잭션(Compensating Transaction)**에 해당한다. 결제 실패/취소 시 이미 커밋된 재고 차감을 되돌리는 유일한 경로다.

### 1.3 기능 범위

| 기능 | 권한 | 프로젝트 상태 조건 |
| --- | --- | --- |
| 리워드 등록 | 프로젝트 창작자 | 종료(성공/실패/취소)되지 않았을 것 |
| 리워드 목록/단건 조회 | 공개 | — |
| 리워드 수정 (공개 전) | 프로젝트 창작자 | 이름·설명·가격·수량 자유 수정 |
| 리워드 수정 (공개 후) | 프로젝트 창작자 | **수량 추가(`increaseQuantity`)만** 허용 |
| 리워드 삭제 | 프로젝트 창작자 | 공개 전만. 공개 후엔 거부 |
| 수량 축소 | ADMIN | 공개 중(진행중)인 프로젝트만, 판매분 미만 불가 |
| 비활성화 | ADMIN | 공개 중(진행중)인 프로젝트만 |
| 재고 차감/복원 | order-service (내부망) | 차감은 프로젝트가 `open`일 때만 |

## 2. 도메인 모델

### 2.1 Reward

한 프로젝트에 속한 후원 옵션 하나. 프로젝트와는 **ID로만 참조**한다(같은 서비스, 별도 애그리거트). Project와 Reward는 향후 별도 서비스로 분리할 계획이라, 지금부터 JPA 연관관계를 만들지 않고 테스트 컨테이너/DB도 분리해 둔다.

| 필드 | 의미 | 제약조건 |
| --- | --- | --- |
| `rewardId` | PK | `IDENTITY` |
| `projectId` | 소속 프로젝트 id | `NOT NULL`, FK 없음 |
| `idempotencyKey` | 생성 중복 방지 키 | `NOT NULL`, `updatable = false`, `(project_id, idempotency_key)` 유일 |
| `name` | 리워드 이름 | `NOT NULL`. 검색 색인 대상(`rewardNames`) |
| `description` | 설명 | nullable |
| `price` | 가격 | `NOT NULL`, 0원 초과 |
| `totalQuantity` | 총 수량 | **`null`이면 무제한 리워드** |
| `remainingQuantity` | 남은 수량 | `totalQuantity`가 null이면 함께 null |
| `version` | 낙관적 락 버전 | `@Version` |
| `active` | 판매 활성 여부 | `NOT NULL`, 기본 `true` |

`BaseEntity`를 상속해 생성/수정 시각이 JPA Auditing으로 자동 관리된다.

주요 도메인 규칙:

- **생성**: `Reward.register(...)` — 가격은 0원 초과, 수량은 지정 시 0 이상, `idempotencyKey` 필수. `remainingQuantity`는 `totalQuantity`와 같은 값으로 시작
- **무제한 리워드**: `totalQuantity == null`이면 재고 검증·차감·복원을 전부 건너뛴다. `soldQuantity()`도 0으로 취급한다(판매량을 추적하지 않았으므로)
- **수량은 늘리는 것만 창작자 권한**: `increaseQuantity`는 창작자가 호출 가능, `decreaseQuantity`는 관리자 전용. 축소는 락 경합과 판매분 초과 위험이 있어 권한을 나눴다
- **판매분 보존 불변 조건**: `decreaseQuantity`, `updateBeforePublish` 모두 `soldQuantity()`(= `totalQuantity - remainingQuantity`) 밑으로 총수량을 줄일 수 없다. `updateBeforePublish`가 "공개 전이라 판매된 적 없다"고 가정하지 않는 이유는, 프로젝트가 아직 `PENDING_REVIEW`여도 order-service가 프로젝트 상태를 확인하지 않고 재고를 차감할 수 있는 경로가 있기 때문이다
- **삭제 대신 비활성화**: 공개 후 삭제 요청은 `deactivate()`로 `active = false`만 바꾼다. 이미 후원 데이터가 이 레코드를 참조하고 있을 수 있어 레코드 자체는 보존한다
- **`isOrderable()`은 부모 프로젝트를 보지 않는다**: `active && (무제한 || 재고 > 0)`. 마감된 프로젝트의 리워드가 `orderable=false`가 되는 것은 Project가 마감 시 `deactivateAllByProject`를 호출해주기 때문이다

### 2.2 StockChangeLog

order-service가 보낸 재고 변경 요청의 중복 도착을 판별하는 멱등성 기록이다(#195).

| 필드 | 의미 | 제약조건 |
| --- | --- | --- |
| `id` | PK | `IDENTITY` |
| `orderId` | 요청을 보낸 주문 id | `NOT NULL` |
| `rewardId` | 대상 리워드 id | `NOT NULL` |
| `operation` | `DECREASE` / `RESTORE` | `NOT NULL`, `EnumType.STRING` |

`(order_id, reward_id, operation)` 유일 제약 위반이 곧 **"이미 처리된 요청"이라는 신호**다. `operation`까지 키에 포함하는 이유: 같은 `(orderId, rewardId)`라도 `DECREASE`(주문 시 차감)와 `RESTORE`(이후 취소 시 복원)는 서로 다른 정상 이벤트다.

## 3. 상태 전이

Reward에는 명시적 status enum이 없다. 대신 `active`와 재고 두 축이 판매 가능 여부를 결정한다.

```
등록 (active=true, remaining=total)
  ├→ 차감 → 재고 소진 (active=true, remaining=0)   [orderable=false, 복원되면 다시 판매 가능]
  ├→ deactivate() → 판매 종료 (active=false)        [되돌리는 경로 없음]
  └→ 하드 삭제 (공개 전만)                            [레코드 제거]
```

| 상태 | 의미 | 진입 조건 |
| --- | --- | --- |
| 판매 중 (`orderable=true`) | 주문 가능 | `active=true` && (무제한 \|\| `remainingQuantity > 0`) |
| 재고 소진 | 활성이지만 재고 없음 | `remainingQuantity == 0`. `restoreStock`/`increaseQuantity`로 다시 판매 가능해질 수 있다 |
| 판매 종료 (`active=false`) | 주문 불가 | 관리자 `deactivate`, 또는 부모 프로젝트 마감 시 `deactivateAllByProject` |
| 삭제됨 | 레코드 없음 | 공개 전 창작자 삭제, 또는 프로젝트 삭제 시 `deleteAllByProject` |

허용/금지되는 전이:

- **`active=false` → `true`로 되돌리는 API는 없다.** 판매 종료는 단방향이다. 마감된 프로젝트의 리워드를 다시 열 필요가 없고, 잘못 비활성화한 경우 되살릴 경로가 없는 것은 §9에 한계로 적어 뒀다
- **재고 소진은 상태가 아니라 값**이다. 복원되면 자동으로 다시 주문 가능해진다
- 공개 후 하드 삭제는 금지 — `delete()`가 `IllegalStateException`으로 거부하고, 관리자 `deactivate`로 유도한다

## 4. API

### 4.1 외부 API

| Method | Path | 요청 | 응답 | 동작 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/projects/{projectId}/rewards` | `RewardCreateRequest` + `X-User-Id` | `RewardResponse` | 리워드 등록 (창작자) |
| GET | `/api/v1/projects/{projectId}/rewards` | — | `List<RewardResponse>` | 프로젝트의 리워드 목록 |
| GET | `/api/v1/rewards/{rewardId}` | — | `RewardResponse` | 리워드 단건 조회 |
| PATCH | `/api/v1/rewards/{rewardId}` | `RewardUpdateRequest` + `X-User-Id` | `RewardResponse` | 수정 (공개 전/후 규칙 다름) |
| DELETE | `/api/v1/rewards/{rewardId}` | `X-User-Id` | `null` | 하드 삭제 (공개 전만) |
| PATCH | `/api/v1/rewards/{rewardId}/quantity` | `RewardQuantityDecreaseRequest` + `X-User-Role: ADMIN` | `RewardResponse` | 수량 축소 (관리자) |
| POST | `/api/v1/rewards/{rewardId}/deactivate` | `X-User-Role: ADMIN` | `null` | 판매 종료 (관리자) |

### 외부 API 데이터 스키마

```jsonc
// RewardCreateRequest
{
  "name": "얼리버드 1+1",            // @NotBlank
  "description": "선착순 100개",
  "price": 29000,                    // @NotNull, 0원 초과
  "totalQuantity": 100,              // @PositiveOrZero, 생략 시 무제한 리워드
  "idempotencyKey": "uuid"           // 선택 — 미전송 시 서버가 랜덤 생성(중복 방지 미적용)
}

// RewardUpdateRequest — 공개 전: 앞 4개 자유 수정 / 공개 후: increaseQuantity만
{
  "name": null,
  "description": null,
  "price": null,
  "totalQuantity": 150,              // null이면 "미변경"
  "clearTotalQuantity": false,       // 유한 → 무제한으로 되돌릴 때 true (totalQuantity와 동시 지정 불가)
  "increaseQuantity": null           // 공개 후 전용, @Positive
}

// RewardQuantityDecreaseRequest (ADMIN)
{ "amount": 10 }                     // @NotNull @Positive

// RewardResponse
{
  "rewardId": 1, "projectId": 10,
  "name": "얼리버드 1+1", "description": "선착순 100개",
  "price": 29000,
  "totalQuantity": 100, "remainingQuantity": 42,
  "orderable": true, "active": true
}
```

`totalQuantity: null`은 PATCH에서 "미변경"을 뜻하므로, 유한 수량을 무제한으로 되돌리려면 `clearTotalQuantity: true`를 써야 한다. 둘을 동시에 지정하면 400이다. 이미 판매된 수량이 있으면 무제한으로 되돌릴 수 없다.

### 4.2 내부 API

| Method | Path | 호출 서비스 | 동작 |
| --- | --- | --- | --- |
| POST | `/internal/v1/rewards/{rewardId}/decrease-stock` | order-service | 재고 차감 (멱등) |
| POST | `/internal/v1/rewards/{rewardId}/restore-stock` | order-service | 재고 복원 (멱등, SAGA 보상) |

### 내부 API 데이터 스키마

```jsonc
// StockChangeRequest — 두 엔드포인트 공통
{
  "quantity": 1,        // @NotNull @Positive
  "orderId": 12345      // @NotNull — (orderId, rewardId, operation) 멱등키의 일부
}
```

응답 본문은 없다(`ApiResponse` envelope만). **중복 요청도 200으로 응답한다** — 재고를 다시 반영하지 않고 조용히 종료하는 no-op이다.

신뢰 경계: `/internal/v1`은 gateway에 라우트가 없는 내부망 전용 경로다(팀 컨벤션). JWT도, `X-User-Id`/`X-User-Role` 헤더도 요구하지 않고 호출자를 신뢰한다. 대신 order-service의 `RewardFeignClient` 경로와 **정확히 일치해야 하는 런타임 계약**이라 어느 한쪽만 바꾸면 즉시 깨진다.

외부 API의 인가: 창작자 대상 API는 리워드 자신에게 `creatorId`가 없어 **부모 프로젝트의 `creatorId`와 `X-User-Id`를 비교**한다(`validateOwnership`). 관리자 API 두 개는 컨트롤러가 `X-User-Role`을 직접 확인한다.

## 5. 주요 처리 흐름

### 5.1 재고 차감 (정상 흐름)

```
RewardInternalController
→ RewardServiceImpl.decreaseStock                  [트랜잭션 없음 — NOT_SUPPORTED]
→ RewardStockTransactionExecutor.decreaseStock     [프록시 경유, 새 트랜잭션]
   1. StockChangeLog 저장 (멱등키)
   2. quantity > 0 검증
   3. ProjectService.findStatusView(projectId) → open 여부 확인
   4. reward.isActive() 확인
   5. 무제한 리워드면 여기서 종료 (no-op)
   6. RewardRepository.decreaseStockAtomic(rewardId, quantity)
→ 커밋
```

핵심은 6번이다. 엔티티를 읽어 수정 후 flush하는 낙관적 락 대신, **조건부 원자적 `UPDATE`**를 쓴다.

```sql
UPDATE Reward r
   SET r.remainingQuantity = r.remainingQuantity - :quantity,
       r.version = r.version + 1
 WHERE r.rewardId = :rewardId
   AND r.active = true
   AND r.remainingQuantity >= :quantity
```

- "활성 상태인가", "재고가 충분한가"를 **DB가 WHERE 절에서 한 번에 검증·반영**한다. 초과 판매는 애플리케이션 조건문이 아니라 이 WHERE 절이 막는다
- 영향 행 수 `0`이 곧 실패다. 정확한 원인(비활성 / 재고 부족)은 실패 후 재조회해 구분한다
- `version`도 함께 증가시킨다 — `update()`/`decreaseQuantity()` 같은 엔티티 기반 낙관적 락 경로가 이 변경을 계속 감지할 수 있도록
- 무제한 리워드(`totalQuantity == null`)는 `remainingQuantity`도 null이라 이 쿼리와 맞지 않는다. 호출부가 아예 호출하지 않는다. 그래서 WHERE 절의 `active = true`에도 닿지 못하므로, **`isActive()` 체크를 애플리케이션에서 먼저** 해야 비활성화된 무제한 리워드도 막힌다

`restoreStock`은 대칭이다 — `remainingQuantity + :quantity <= totalQuantity`를 WHERE 절에서 원자적으로 검증한다. 복원 시에는 프로젝트가 `open`인지 확인하지 않는다(마감된 프로젝트의 환불도 재고를 되돌려야 하므로).

이 방식은 원래 낙관적 락 + `@Retryable`이었다가 바꾼 것이다. 고경합(플래시 세일) 상황에서 재시도를 반복 소진해 **재고가 남았는데도 실패**하는 문제가 있었고, 조건부 UPDATE는 재시도 자체가 필요 없다.

### 5.2 트랜잭션 경계를 나눈 이유

`RewardServiceImpl.decreaseStock`은 `@Transactional(propagation = NOT_SUPPORTED)`로 트랜잭션을 열지 않고, 실제 본문은 별도 빈 `RewardStockTransactionExecutor`가 갖는다. 이 분리는 **멱등성 처리를 위해 반드시 필요하다.**

```
중복 요청 도착
→ StockChangeLog.save()  → 유니크 제약 위반
→ Hibernate flush 실패 → 트랜잭션이 rollback-only로 표시됨
→ 예외를 트랜잭션 경계 밖으로 내보냄 → Spring이 정상 rollback으로 마무리
→ RewardServiceImpl(트랜잭션 없음)에서 DataIntegrityViolationException catch → no-op 종료 (200)
```

같은 트랜잭션 안에서 catch하고 정상 반환하면, 이미 rollback-only로 표시된 트랜잭션을 커밋하려다 `UnexpectedRollbackException`이 난다. 그래서 "이미 처리된 요청"이라는 판단은 트랜잭션이 **완전히 끝난 뒤** 바깥에서 내려야 한다.

`register()`도 정확히 같은 구조다 — `(projectId, idempotencyKey)` 유일 제약 충돌을 트랜잭션 밖에서 잡아 기존 리워드를 조회해 반환한다. 그리고 `register()`는 **idempotency 조회를 소유권/종료 검증보다 먼저** 한다. 응답 유실 후 재시도 시점엔 프로젝트가 이미 종료됐을 수 있는데, 검증을 먼저 하면 "이미 만든 리워드를 그대로 반환해야 할" 재시도가 `IllegalStateException`으로 잘못 거부된다.

> `RewardStockTransactionExecutor`의 트랜잭션 메서드는 반드시 `public`이어야 한다 — Spring 프록시 기반 `@Transactional`은 public 메서드만 인터셉트하고, package-private/protected는 애노테이션이 있어도 조용히 무시한다.

### 5.3 엔티티 기반 쓰기 경로 (`update`, `decreaseQuantity`)

이 둘은 조건부 UPDATE가 아니라 엔티티를 읽어 수정하므로 여전히 낙관적 락 충돌이 난다 — 관리자가 수량을 줄이는 동안 다른 후원자의 `decreaseStock`이 같은 리워드의 `version`을 올리는 경우다. 그래서 `@Retryable(ObjectOptimisticLockingFailureException, maxAttempts=3, backoff=50ms)`을 붙이고, 소진 시 `@Recover`가 `ConcurrentUpdateFailedException`(→ 409)으로 변환한다.

**`@Recover`는 반드시 catch-all과 쌍으로 둔다.**

```java
@Recover
public RewardResponse recoverUpdateConflict(ObjectOptimisticLockingFailureException e, ...) { ... }

@Recover
public RewardResponse recoverUpdateOther(RuntimeException e, ...) { throw e; }
```

Spring Retry는 `@Recover`가 하나라도 있으면 `retryFor`에 없는 예외까지 복구 경로 탐색 대상으로 삼는다. 매칭되는 시그니처가 없으면 `ExhaustedRetryException`으로 원래 예외를 삼켜 **409여야 할 응답이 500으로 나간다.** `update()`의 검증 예외(`IllegalArgumentException`/`IllegalStateException`)가 그대로 마스킹되므로 catch-all이 필수다. 파라미터 시그니처가 다르면 `@Recover`를 공유할 수 없어 메서드마다 전용 쌍을 둔다.

`@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)`도 필수다 — 순서를 명시하지 않으면 retry advisor가 `@Transactional` 안쪽에 들어가 재시도마다 이미 실패가 확정된 트랜잭션을 재사용하고, 엔티티의 최신 `version`을 다시 읽지 못한다.

### 5.4 실패 및 보상 흐름

```
order-service: 주문 생성
→ POST decrease-stock
   → 성공: 재고 차감 확정, StockChangeLog(DECREASE) 기록
   → 확정 실패(409): 재고 부족/판매 종료/프로젝트 마감 — 주문 실패 처리
   → 중복 도착: 200 no-op (재고 재차감 없음)
→ 이후 결제 실패 / 사용자 취소 / 프로젝트 실패 일괄 환불
→ POST restore-stock  ← SAGA 보상 트랜잭션
   → 성공: 재고 복원, StockChangeLog(RESTORE) 기록
   → 중복 도착: 200 no-op
```

- **확정 실패**(재고 부족, 비활성 리워드, 마감 프로젝트)는 409로 정직하게 알린다. 재시도해도 결과가 같다
- **결과 불명**(네트워크 타임아웃)은 order-service가 같은 `orderId`로 재시도하면 된다 — `StockChangeLog`가 중복을 흡수하므로 재시도가 안전하다
- **보상 처리**는 `restoreStock` 하나뿐이다. 차감 이후의 어떤 실패든 이 경로로 되돌린다
- 프로젝트가 마감/취소되면 Project 도메인이 `deactivateAllByProject`로 리워드를 일괄 비활성화한다. 이는 보상이 아니라 후속 상태 정리다

## 6. 데이터 저장 구조

```
RewardServiceImpl / RewardStockTransactionExecutor
→ RewardRepository, StockChangeLogRepository (JpaRepository)
→ Hibernate / MySQL
→ rewards, stock_change_logs
```

| 항목 | 내용 |
| --- | --- |
| 테이블 | `rewards`, `stock_change_logs` |
| 유니크 | `uk_rewards_project_id_idempotency_key (project_id, idempotency_key)` / `stock_change_logs (order_id, reward_id, operation)` |
| 외래 키 | **없다.** `rewards.project_id`는 값 참조 |
| 체크 제약 | 없다. 가격/수량 검증은 엔티티가, 재고 하한/상한은 조건부 UPDATE의 WHERE 절이 담당 |
| 동시성 제어 | 재고: **조건부 원자적 UPDATE**. 그 외 엔티티 수정: `@Version` 낙관적 락 + `@Retryable` |
| 같은 트랜잭션에서 처리해야 하는 데이터 | `StockChangeLog` 기록과 재고 변경 — 둘이 갈라지면 멱등성이 깨진다 |

`RewardRepository`의 주요 메서드:

| 메서드 | 용도 |
| --- | --- |
| `findByProjectId` | 리워드 목록 조회, `deactivateAllByProject`, 색인 시 `rewardNames` 수집 |
| `findByProjectIdAndIdempotencyKey` | 생성 중복 방지 |
| `findByProjectIdIn` | 벌크 재색인 시 프로젝트별 N+1 조회 방지 |
| `deleteByProjectId` | 프로젝트 삭제 시 일괄 제거 |
| `decreaseStockAtomic` / `restoreStockAtomic` | `@Modifying(clearAutomatically = true)` 조건부 UPDATE |

`clearAutomatically = true`는 UPDATE 후 영속성 컨텍스트를 비워, 같은 트랜잭션에서 이후에 읽는 엔티티가 stale한 재고를 보지 않게 한다.

Project와 Reward는 향후 별도 서비스로 분리할 계획이라 테스트 컨테이너와 DB를 지금부터 분리해 두고 있다.

## 7. 예외 처리와 장애 복구

| 장애 상황 | 판별 기준 | 처리 방식 | 최종 상태 |
| --- | --- | --- | --- |
| 존재하지 않는 리워드/프로젝트 | `findById` empty / `findStatusView` empty | `EntityNotFoundException` | 404 |
| 재고 부족 | `decreaseStockAtomic` 영향 행 0 + 재조회 시 active | `IllegalStateException` | 409 |
| 판매 종료된 리워드 주문 | `isActive() == false` | `IllegalStateException` | 409 |
| 마감/미진행 프로젝트의 리워드 주문 | `ProjectStatusView.open() == false` | `IllegalStateException` | 409 |
| 복원 후 총수량 초과 | `restoreStockAtomic` 영향 행 0 | `IllegalStateException` | 409 |
| 중복 재고 변경 요청 | `StockChangeLog` 유니크 위반 | **catch 후 no-op** | 200 |
| 중복 생성 요청 | `(projectId, idempotencyKey)` 유니크 위반 | 기존 리워드 조회해 반환 | 200 |
| 낙관적 락 충돌 반복 | `@Retryable` 3회 소진 | `ConcurrentUpdateFailedException` | 409 |
| 소유자 아님 / 잘못된 요청 조합 | `validateOwnership`, 필드 조합 검증 | `IllegalArgumentException` | 400 |

- **외부 연동 timeout**: Reward 도메인은 밖으로 나가는 HTTP 호출이 없다. 자기가 호출당하는 쪽이라 서킷브레이커도 두지 않는다. Project 도메인 조회(`findStatusView`)는 같은 서비스 안 메서드 호출이다
- **복구 배치 / 수동 복구**: 재고 정합성을 되돌리는 자동 배치는 없다. 재고가 어긋났다면 `stock_change_logs`를 주문 이력과 대조해 수동으로 복구해야 한다
- **확정 실패와 결과 불명의 구분**: 409는 확정 실패(재시도해도 같은 결과), 네트워크 오류는 결과 불명이며 호출자가 같은 `orderId`로 재시도하면 멱등하게 처리된다

## 8. 테스트 현황

| 테스트 | 검증 범위 |
| --- | --- |
| `RewardTest` | 엔티티 규칙 — 가격/수량 검증, 차감·복원 경계, `increaseQuantity`/`decreaseQuantity` 판매분 하한, `updateBeforePublish` 조합, `isOrderable` |
| `RewardServiceImplOwnershipTest` | 창작자 소유권 검증, 종료된 프로젝트 거부, 공개 전/후 수정 규칙 분기 |
| `RewardServiceImplRetryTest` | 낙관적 락 충돌 재시도, `@Recover`의 409 변환, catch-all이 검증 예외를 마스킹하지 않는지 |
| `RewardServiceImplStockChangeIdempotencyTest` | 중복 `(orderId, rewardId, operation)` 요청의 no-op 처리 |
| `RewardStockIdempotencyIntegrationTest` | 실제 DB 유니크 제약 기반 멱등성 (Testcontainers) |
| `RewardConcurrencyIntegrationTest` | 동시 차감/복원에서 초과 판매·과다 복원이 발생하지 않는지 (Testcontainers) |
| `StockChangeLogRepositoryTest` | 멱등키 유니크 제약 동작 |
| `RewardControllerTest` | 권한 헤더, `@Valid`, 응답 envelope |
| `RewardInternalControllerTest` | 내부 API 경로/요청 스키마 계약 |

부하 테스트 스크립트가 `project-service/k6/`에 있다 — `reward-stock-load-test.js`, `reward-restore-stock-load-test.js`.

테스트하지 못한 시나리오:

- **다중 인스턴스 동시 차감.** 조건부 UPDATE라 이론적으로는 인스턴스 수와 무관하지만 실제로 검증하지는 않았다
- **`decrease-stock` 성공 후 order-service가 응답을 못 받아 복원도 재차감도 하지 않는 경우** — 고아 차감분이 남는다. 이를 탐지·정리하는 경로가 없다

## 9. 현재 한계와 후속 과제

- **`idempotencyKey`가 선택 필드다.** 미전송 클라이언트와의 하위 호환을 위해 필수로 두지 않았고, 미전송 시 서버가 랜덤 UUID를 만들어 중복 방지가 적용되지 않는다.
    - 대응 계획: 프론트엔드가 전 경로에서 키를 보내는 것이 확인되면 `@NotNull`로 승격.
- **`active=false`를 되돌리는 API가 없다.** 관리자가 실수로 비활성화하면 DB를 직접 고치는 수밖에 없다.
    - 대응 계획: 관리자 `activate` API 추가 검토. 단, 마감된 프로젝트의 리워드가 되살아나지 않도록 프로젝트 상태 조건을 함께 걸어야 한다.
- **고아 차감분을 탐지하는 경로가 없다.** `decrease-stock`이 성공했는데 order-service가 응답을 받지 못해 주문을 롤백한 경우, 재고만 깎이고 복원되지 않는다.
    - 대응 계획: `stock_change_logs`와 order-service의 주문 목록을 대조하는 정합성 점검 배치. 현재는 `fundedAmount` 보정 스케줄러처럼 pull 방식으로 맞추는 경로가 재고에는 없다.
- **무제한 리워드는 판매량을 추적하지 않는다.** `soldQuantity()`가 0을 반환하므로, 무제한 리워드를 유한 수량으로 바꾸면 그동안 팔린 수량이 반영되지 않는다.
    - 대응 계획: 무제한 리워드에도 판매 카운터를 두거나, 무제한 → 유한 전환을 금지.
- **`/internal/v1`이 인증 없이 호출자를 신뢰한다.** 내부망 전제이므로 네트워크가 분리돼 있지 않으면 누구나 재고를 조작할 수 있다.
    - 대응 계획: 서비스 간 mTLS 또는 내부 토큰. 인프라 구성과 함께 다뤄야 해서 project-service 단독으로 결정하지 않는다.
- **재고 정합성 복구가 전적으로 수동이다.** 어긋났을 때 이를 알려주는 모니터링도 없다.
    - 대응 계획: `remainingQuantity < 0` 같은 불변 조건 위반을 감지하는 알림부터 추가.
