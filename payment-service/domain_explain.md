# Payment 도메인 구현 현황

담당자: @[단기심화7] 정창민
기준일: 2026-08-24

이 문서는 목표 설계가 아니라 현재 `payment-service` 구현을 기준으로 작성한다.

## 1. 책임 범위

Payment 서비스는 주문별 결제 준비, Toss 승인·조회, 결제 상태 정합화, 전액 환불, 결제 상태 Kafka 통지를 담당한다.

- Order 서비스: 주문 생성·주문 소유권·주문 취소 가능 여부 검증
- Payment 서비스: Payment/Refund 상태 전이와 Toss 연동
- FE: Payment 조회 후 Toss 결제창 호출, Toss success URL에서 승인 API 호출

```text
Order → Payment: 결제 준비
FE → Payment: 준비 결제 조회 → Toss 결제창 호출
FE → Payment: 승인
Payment → Toss: 승인 또는 결제 상태 조회
Payment → Kafka: 결제 상태 통지
Order ← Kafka: 결제 상태 반영
```

## 2. 도메인 모델

### Payment

`payments` 테이블의 주문당 단일 결제다. `order_id`, `pg_order_id`는 유니크하다.

| 필드 | 의미 |
| --- | --- |
| `paymentId` | 내부 결제 식별자 |
| `userId` | 결제 요청자 식별자 |
| `orderId` | Order 서비스 주문 식별자 |
| `pgOrderId` | `order-{orderId}-{UUID}` 형식의 Toss 주문 번호 |
| `paymentKey` | Toss 결제 키. `CONFIRMING` 전이 시 저장 |
| `amount` | 준비·승인·환불 시 검증하는 전액 금액 |
| `approveIdempotencyKey` | Toss 승인 요청에 사용하는 서버 생성 UUID |
| `version` | 승인·정합화 상태 전이의 낙관적 락 버전 |
| `confirmingAt` | 승인 선점 시각 |
| `confirmedAt` / `canceledAt` | 승인·취소 완료 시각 |

`paymentKey`, `approveIdempotencyKey`, `Refund.cancelIdempotencyKey`는 `SensitiveValue`와 JPA `AttributeConverter`로 AES-256-GCM 암호화 저장한다. 암호화 키는 `PAYMENT_SECURITY_ENCRYPTION_KEY` 환경변수에서 받는다.

### Refund

`refunds` 테이블의 결제당 단일 전액 환불 이력이다. `payment_id`는 유니크하다.

| 필드 | 의미 |
| --- | --- |
| `paymentId` | 환불 대상 Payment |
| `refundRequestId` | 일괄 환불 요청 식별자. 사용자 취소는 `null` |
| `amount` | Payment에 저장된 전액 금액 |
| `reason` | `USER_CANCEL`, `GOAL_FAILED` |
| `status` | `PLANNED`, `REQUESTED`, `RETRY_PENDING`, `COMPLETED`, `FAILED` |
| `version` | 환불 완료·실패·재시도 상태 전이의 낙관적 락 버전 |
| `retryCount` / `nextRetryAt` | 환불 복구 재시도 정보 |

### PaymentStatusOutbox

결제 상태 변경을 Order에 전달하기 위한 Outbox다. `(payment_id, payment_status)`는 유니크하며 상태는 `PENDING`, `PROCESSING`, `SENT`다.

## 3. 상태 전이

### Payment

```text
READY → CONFIRMING → PAID → CANCELLED
  └──────────────→ FAILED
FAILED ── Toss 정합화 결과 COMPLETED ──→ PAID
```

- `READY`: 결제 준비 완료
- `CONFIRMING`: 한 요청이 승인 권한을 선점한 상태
- `PAID`: Toss 승인 검증 완료
- `FAILED`: 확정 승인 실패 또는 대기 시간 초과
- `CANCELLED`: Toss 취소 완료 후 전액 환불이 반영된 상태

`FAILED → PAID`는 응답 유실 뒤 복구 배치나 웹훅 조회가 Toss의 `COMPLETED`를 확인했을 때만 허용한다.

### Refund

```text
PLANNED → REQUESTED → COMPLETED
                 ├→ RETRY_PENDING → REQUESTED
                 └→ FAILED
```

사용자 취소는 바로 `REQUESTED`로 생성한다. 일괄 환불은 `PLANNED`로 만들고 스케줄러가 `REQUESTED`로 선점한 뒤 Toss 취소를 호출한다.

## 4. API와 소유권 검증

### 외부 API

| Method | Path | 동작 |
| --- | --- | --- |
| POST | `/api/v1/payments/confirm` | JWT 사용자 검증 후 Toss 승인 요청 |
| GET | `/api/v1/payments/{paymentId}` | JWT 사용자 소유 Payment 조회 |
| GET | `/api/v1/payments?orderId={orderId}` | JWT 사용자 소유 주문의 Payment 조회 |
| POST | `/api/v1/payments/webhook` | Toss `paymentKey`로 실제 PG 상태를 재조회해 정합화 |

### 내부 API

| Method | Path | 동작 |
| --- | --- | --- |
| POST | `/internal/v1/payments/prepare` | `userId`, `orderId`, `amount`으로 READY Payment 준비 |
| POST | `/internal/v1/payments/cancel` | `orderId`, `paymentId`를 검증한 뒤 전액 환불 시작 |
| GET | `/internal/v1/payments?orderId={orderId}` | Order 서비스용 Payment 조회 |

사용자 취소는 Order 서비스가 사용자·주문 상태를 검증한 뒤 내부 취소 API를 호출한다. Payment 서비스는 전달받은 `orderId`와 `paymentId`가 실제로 연결됐는지 추가 검증한다.

## 5. 결제 준비

1. Order가 `userId`, `orderId`, `amount`로 [내부 Prepare API](src/main/java/com/growmighty/lectures/firstday/payment/presentation/PaymentInternalController.java)를 호출한다.
2. 기존 Payment가 없으면 `READY` Payment와 `pgOrderId`, 승인 멱등 키를 생성한다.
3. 같은 `userId`, `orderId`, `amount`의 재요청은 기존 READY Payment를 반환한다.
4. 동시 생성으로 `order_id` 유니크 충돌이 발생하면, 생성 트랜잭션을 종료한 뒤 새 트랜잭션에서 기존 Payment를 조회해 반환한다.
5. 사용자·금액이 다르거나 기존 상태가 `CONFIRMING`, `PAID`, `FAILED`, `CANCELLED`면 요청을 거절한다.

[PaymentService](src/main/java/com/growmighty/lectures/firstday/payment/application/PaymentService.java) 는 유니크 충돌을 조정하고, 실제 생성·재조회 트랜잭션은 [PaymentPreparationService.java](src/main/java/com/growmighty/lectures/firstday/payment/application/PaymentPreparationService.java) 가 담당한다. 같은 객체 내부 호출로 트랜잭션이 무시되는 문제를 피하기 위한 분리다.

## 6. 승인 Saga와 복구

```text
READY
  │ startConfirmation() 트랜잭션
  ▼
CONFIRMING ── commit ──→ Toss 승인 호출 (트랜잭션 없음)
  │                              │
  │ 성공                         │ 확정 실패
  ▼                              ▼
PAID                           FAILED
  ▲
  └── 결과 불명: 웹훅 또는 복구 배치의 Toss 조회 후 정합화
```

1. [PaymentConfirmationService](src/main/java/com/growmighty/lectures/firstday/payment/application/PaymentConfirmationService.java)`.startConfirmation()`이 `READY → CONFIRMING`을 저장하고 승인 대상 정보를 반환한다.
2. `[PaymentApprovalSagaOrchestrator](src/main/java/com/growmighty/lectures/firstday/payment/application/PaymentApprovalSagaOrchestrator.java) 가 DB 트랜잭션 없이 Toss 승인 API를 호출한다.
3. 성공 응답의 paymentKey·pgOrderId·amount를 검증한 뒤 `PAID`로 전이한다.
4. `DEFINITIVE` 실패는 `FAILED`로 전이한다. 네트워크 오류, 5xx, 408, 429처럼 처리 결과가 불명인 `UNCERTAIN` 실패는 `CONFIRMING`으로 남긴다.
5. READY → CONFIRMING 선점의 `@Version` 충돌은 `PaymentConfirmationInProgressException`으로 변환해 동시 승인 요청을 거절한다. PAID 완료 저장에서 충돌하면 다른 경로가 이미 PAID로 확정했는지 재조회해, PAID이면 정상 결과를 반환하고 아니면 예외를 전파한다.

`CONFIRMING` 결제는 기본 3분 경과 후 복구 대상이며, 복구 배치는 최대 100건씩 Toss 상태를 조회한다. Toss가 계속 `PENDING`이고 기본 10분을 초과하면 `FAILED`로 전이한다. `READY` 결제는 기본 30분을 초과하면 `FAILED`로 만료한다.

## 7. Toss 장애 대응

승인·조회·환불에는 각각 독립된 Resilience4j `RateLimiter`를 둔다. 각 Limiter는 750ms당 1건을 허용하며 대기 없이 거절한다.

```text
Circuit Breaker → Retry → RateLimiter → Toss API
```

- 승인·환불: 최대 3회, 조회: 최대 2회 재시도
- 재시도 간격: 1초
- Circuit Breaker: 최근 10건, 최소 5건 이후 실패율 50%에서 open, 30초 뒤 half-open 2건 허용
- Retry와 Circuit Breaker는 `UNCERTAIN` 실패만 대상으로 한다.

## 8. 환불·취소 Saga와 복구

```text
PAID Payment
  → Refund REQUESTED 저장
  → Toss 취소 호출
  → 성공: Refund COMPLETED + Payment CANCELLED + 상태 Outbox
  → 확정 실패: Refund FAILED
  → 결과 불명: Refund RETRY_PENDING
```

- 환불 금액은 요청값이 아니라 저장된 `Payment.amount`를 사용한다.
- Toss 취소 성공 후 Refund 완료와 Payment 취소는 같은 트랜잭션에서 반영한다.
- Refund의 완료·실패·재시도 상태 전이는 `@Version`으로 낙관적 락을 적용한다.
- `REQUESTED` 환불이 기본 3분을 초과하면 복구 배치가 Toss 상태를 조회한다.
- Toss 조회 결과가 `CANCELLED`면 완료, `COMPLETED`·`FAILED`·`EXPIRED`면 실패, `PENDING`이면 재시도를 예약한다.
- 일괄 환불 결과는 별도 `BulkRefundResultOutbox`에 저장해 Kafka 발행한다.

## 9. 결제 상태 Outbox와 Kafka

결제 상태 전이와 `PaymentStatusOutbox(PENDING)` 저장은 같은 DB 트랜잭션에서 수행한다.

```text
상태 전이 + PENDING Outbox 저장
                │
             DB commit
                │
       AFTER_COMMIT 즉시 발행
                │
PENDING ── claim ──→ PROCESSING ── Kafka 성공 ──→ SENT
                │
             발행 실패
                ▼
             PENDING 유지 → 스케줄러 재시도
```

- `@TransactionalEventListener(AFTER_COMMIT)`가 새 트랜잭션에서 즉시 `payment.single-result.v1`에 Kafka 이벤트를 발행한다.
- 기본 60초 스케줄러가 `PENDING` Outbox를 재시도한다.
- 발행 전 조건부 update로 `PENDING → PROCESSING`을 선점한다. 5분 이상 PROCESSING이면 PENDING으로 복구한다.
- Kafka 발행 성공 후 SENT 저장 전에 프로세스가 종료되면 재발행될 수 있다. 소비자는 `orderId + paymentStatus` 또는 이벤트 식별자 기준 멱등 처리가 필요하다.

## 10. 웹훅 정합화

Webhook body에는 `eventType`, `paymentKey`가 전달되며 현재 구현은 `paymentKey`를 사용한다. 전달받은 `paymentKey`로 Toss를 다시 조회하고, 응답의 `pgOrderId`, `paymentKey`, `amount`을 Payment와 검증한 뒤 `PaymentReconciliationService`가 상태를 정합화한다.

Webhook 요청 본문만으로 내부 상태를 바꾸지는 않는다. 다만 임의 호출이 Toss 조회 RateLimiter를 소모할 수 있으므로 운영 환경에서는 Gateway ACL, 인바운드 Rate Limit 등 수신 경로 접근 제어가 필요하다.

## 11. 테스트

- `PaymentServiceTest`: prepare 멱등성, 소유권 검증, 승인 요청 회귀
- `PaymentConfirmationServiceConcurrencyTest`: MySQL Testcontainers 기반 동시 승인 선점
- `PaymentConfirmationServiceReconcileTest`, `PaymentReconciliationServiceTest`, `PaymentRecoveryServiceTest`: 승인 실패·PG 상태 정합화·복구
- `PaymentStatusOutbox*Test`: 즉시 발행, 재시도, PROCESSING 복구
- `RefundServiceTest`, `RefundRecoveryServiceTest`: 환불 완료·실패·재시도·복구
- `TossPaymentGatewayTest`, `TossRefundGatewayTest`: Toss 응답 및 오류 분류

## 12. 현재 한계와 후속 과제

- Payment Status Outbox 생성은 `exists → save` 방식이다. 동일 상태 Outbox 생성 경합의 중복 키 예외 정책은 보류 상태다.
  - 해당 부분은 현재로서는 단일 인스턴스 이고, 동일 결제 상태 전이가 낙관적 락으로 대부분 직렬화 되는 구조이다.
  - 유니크 제약으로 데이터 중복은 막고 있고, 실제로 중복 키 예외나, 동시 요청 부하가 관측되면 이슈를 진행할 예정이다. [링크](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/486)
- 다중 인스턴스 환경의 일괄 환불 작업과 일괄 환불 결과 Outbox 발행은 아직 원자적 선점이 없다.
  - 다중 인스턴스 환경에 대한 작업은 추후 이슈로 남긴다. [링크](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/488)
- Payment Status Outbox는 발행 전 `PENDING → PROCESSING`으로 선점하지만, Kafka 성공 후 SENT 저장 전 프로세스 장애가 나면 재발행될 수 있다. 소비 측 멱등성이 필요하다.
  - 해당 부분은 Order-Service에서 Inbox로 eventId를 수신함을 확인했으므로, 멱등성이 보장된다.
- Webhook 수신 경로의 Gateway ACL·인바운드 Rate Limit은 운영 인프라에서 적용해야 한다.
  - 해당 부분은 현재 인프라 차원에서 막는 것이 YAGNI 기법에 따라 가장 간편하지만, AWS 기본 내장 기능을 사용함이 기술적으로 좋지 않은 방향이라면,
  - 별도로 API RATE LIMIT를 추가할 예정이다. 이슈로 남긴다. [링크](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/490)
