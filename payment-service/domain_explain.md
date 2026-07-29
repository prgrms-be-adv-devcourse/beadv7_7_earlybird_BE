# Payment 도메인 구현 현황

담당자: @[단기심화7] 정창민
기준일: 2026-07-28

이 문서는 목표 설계가 아니라 현재 `payment-service` 코드의 구현 상태를 기준으로 작성한다.

## 1. 목적

Payment 도메인은 주문에 대한 결제 준비, PG 승인 요청, 결제 상태 관리, 결제 조회와 승인 결과 복구를 담당한다. 환불은 `refund` 패키지에서 전액 환불 이력 생성과 Toss 취소 요청을 담당한다. 현재 PG 구현체는 테스트용 `FakePaymentGateway`와 토스페이먼츠용 `TossPaymentGateway`를 지원하며, 환불은 `FakeRefundGateway`와 `TossRefundGateway`를 지원한다.

실제 승인 흐름에서는 준비 단계에서 저장한 주문 번호·금액을 기준으로 요청을 검증하고, 토스 승인 성공 후에만 내부 결제를 `PAID`로 변경한다. 승인 응답 유실은 복구 배치와 토스 웹훅으로 보완하며, 두 경로 모두 Toss 결제 조회 결과를 기준으로 같은 정합화 로직을 사용한다.

## 2. 현재 책임 범위

### 구현됨

- 내부 결제 준비: 주문 ID, PG 주문번호, 금액을 `READY` 결제로 저장
- 토스 결제창 인증 후 승인 요청 처리
- 준비된 금액과 승인 요청 금액 비교
- 토스 승인 응답의 주문번호·결제키·금액 검증
- 결제 단건 조회
- `@Version` 기반 낙관적 락으로 동시 승인 선점 보호
- 승인용 멱등키 생성 및 토스 `Idempotency-Key` 헤더 전달
- `paymentKey` 기반 토스 결제 상태 조회
- `paymentKey`, `approveIdempotencyKey`의 AES-256-GCM 암호화 저장 및 JPA 자동 복호화
- 오래된 `CONFIRMING` 결제의 토스 상태 복구
- `CONFIRMING` 최대 대기 시간 초과 시 `PENDING` 결제 실패 처리
- 3분 주기 타임아웃 결제 복구 배치 실행
- Toss `PAYMENT_STATUS_CHANGED` 웹훅 수신 및 Toss 재조회 기반 상태 정합화
- 정합화로 `PAID`가 된 결제의 Order 서비스 상태 통보
- `status`, `confirmingAt` 복합 인덱스를 사용한 복구 대상 조회
- Fake PG 및 Toss PG 구현체 선택
- `PAID` 결제의 전액 환불 이력(`Refund`) 생성
- Toss `POST /v1/payments/{paymentKey}/cancel` 호출 및 `CANCELED` 응답 검증
- 환불 사유(`USER_CANCEL`, `GOAL_FAILED`) 전달

### 아직 구현되지 않음 또는 미완성

- 사용자 소유권 검증 및 내 결제 내역 조회(페이징·상태 필터)
- 승인/취소 이벤트 발행
- 프로젝트 마감 전 취소 가능 여부 검증
- `READY` 상태로 장기 체류한 미결제 건의 만료 정책
- 카드·영수증 등 토스 상세 응답 저장 및 마스킹
- 웹훅 `eventType` 검증 및 인프라 레벨 Toss 인바운드 IP ACL 적용
- 외부 Toss 관리자 화면에서 발생한 취소를 `PAID → CANCELLED`로 정합화하고 Refund 이력을 생성하는 처리
- 환불 결과 재조회·재시도 및 실패 Refund 처리

## 3. 도메인 모델

`Payment` 엔티티는 `payments` 테이블을 사용한다.

| 필드 | 의미 |
| --- | --- |
| `paymentId` | 내부 결제 식별자. DB 컬럼은 `id` |
| `orderId` | 주문 서비스의 주문 식별자. `Long`이며 결제당 하나로 유니크 |
| `pgOrderId` | `order-{orderId}-{32자리 UUID}` 형식의 Toss 주문번호. 유니크, 최대 58자. 웹훅·복구 정합화 시 Payment 조회 키로 사용 |
| `paymentKey` | 토스가 인증 성공 후 발급하는 결제 식별자. `READY → CONFIRMING` 전이 시 저장하며, DB에는 AES-256-GCM 암호문으로 저장 |
| `amount` | 준비·승인 단계에서 검증하는 결제 금액 |
| `approveIdempotencyKey` | 승인 재시도 시 토스에 전달하는 서버 생성 UUID. DB에는 AES-256-GCM 암호문으로 저장 |
| `version` | 낙관적 락 버전 |
| `confirmingAt` | 승인 선점 시각 |
| `confirmedAt` | 내부 승인 완료 시각 |
| `canceledAt` | 내부 취소 처리 시각 |
| `status` | 결제 상태 |

복구 배치 조회를 위해 `payments(status, confirming_At)` 복합 인덱스를 선언한다. 조회 조건인 `status = CONFIRMING`, `confirmingAt < cutoff` 및 정렬에 사용한다.

### 결제 민감정보 암호화

`paymentKey`, `approveIdempotencyKey`는 `PaymentSensitiveDataConverter`를 통해 DB 저장 시 AES-256-GCM으로 암호화하고, 엔티티 조회 시 자동 복호화한다. 암호문은 매 저장마다 생성되는 12바이트 IV와 GCM 인증 태그를 포함한 뒤 Base64 문자열로 저장한다. 따라서 같은 평문도 저장할 때마다 서로 다른 암호문이 된다.

AES 키는 코드나 설정 파일에 저장하지 않으며, `PAYMENT_SECURITY_ENCRYPTION_KEY` 환경변수를 `payment.security.encryption-key`로 바인딩해 주입한다. 값은 Base64로 인코딩한 32바이트 AES-256 키여야 하며, 기존 암호문을 복호화하려면 같은 키를 유지해야 한다.

AES-GCM 암호문은 매번 달라져 `paymentKey`를 DB 동등 조회 조건으로 사용할 수 없다. PG 정합화는 Toss 재조회 응답의 유니크한 `pgOrderId`로 Payment를 찾고, 조회 후 복호화된 `paymentKey`가 Toss 응답의 값과 일치하는지 검증한다.

### 응답 DTO 분리

결제 정보는 용도에 따라 `PaymentInfo`와 `PaymentPreparationInfo`로 나눈다.

| DTO | 사용처 | 포함 정보 | 분리 이유 |
| --- | --- | --- | --- |
| `PaymentInfo` | 승인 결과, 결제 단건 조회, 취소 결과 | `paymentId`, `orderId`, `amount`, `status` | 일반 결제 상태 조회에 필요한 내부 결제 정보만 전달 |
| `PaymentPreparationInfo` | Order의 결제 준비 요청, 토스 결제창 호출 직전 | `paymentId`, `pgOrderId`, `amount`, `status` | 토스 결제창의 `orderId`로 사용할 `pgOrderId`를 전달 |

`pgOrderId`는 Payment가 생성·저장하며, 토스 인증 전 결제창을 열 때만 필수다. 이를 모든 승인·조회 응답에 포함하면 사용처와 무관한 값을 계속 노출하게 되므로, prepare 전용 DTO로 분리한다.

### 환불 모델

`Refund` 엔티티는 `refunds` 테이블을 사용하며, 전액 환불 시도와 완료 결과를 별도로 기록한다.

| 필드 | 의미 |
| --- | --- |
| `id` | 내부 환불 식별자 |
| `paymentId` | 환불 대상 Payment 식별자 |
| `amount` | Payment에 저장된 전액 환불 금액 |
| `reason` | `USER_CANCEL`, `GOAL_FAILED` |
| `status` | `REQUESTED`, `COMPLETED`, `FAILED` |
| `completedAt` | 환불 완료 시각 |

### 상태

```text
READY → CONFIRMING → PAID → CANCELLED
                   └→ FAILED ┄┄(정합화 성공 시)┄┄→ PAID
```

- `READY`: 결제 준비 완료, 아직 PG 승인 전
- `CONFIRMING`: 한 요청이 승인 처리를 선점한 상태
- `PAID`: PG 승인 검증 및 내부 저장 완료
- `FAILED`: 토스 조회 결과가 `ABORTED`, `EXPIRED`, `CANCELED`이거나 `PENDING` 상태가 최대 대기 시간을 넘겼을 때 `CONFIRMING`에서 전이
- `CANCELLED`: 내부 취소 처리 완료 상태

`Payment.reconcileConfirmed()`는 `CONFIRMING`뿐 아니라 `FAILED` 상태에서도 호출을 허용한다. 따라서 한 번 `FAILED`로 전이된 결제라도, 이후 복구 배치나 웹훅이 토스 조회 결과 `DONE`을 받으면 `PAID`로 다시 전이될 수 있다. 이는 늦게 도착한 승인 응답을 구제하기 위한 의도된 동작이며, `FAILED`가 항상 종단 상태는 아니다.

## 4. API

| Method | Path | 현재 동작 |
| --- | --- | --- |
| POST | `/internal/v1/payments/prepare` | `READY` 결제를 생성하거나, 동일한 준비 요청이면 기존 결제를 반환 |
| POST | `/api/v1/payments/confirm` | 금액 검증 → PG 승인 → `PAID` 처리 |
| POST | `/api/v1/payments/webhook` | Toss 웹훅의 `paymentKey`로 Toss를 재조회한 뒤 Payment 상태 정합화 |
| GET | `/api/v1/payments/{paymentId}` | 결제 단건 조회 |
| POST | `/api/v1/payments/{paymentId}/cancel` | 기존 취소 경로. `TossPaymentGateway.cancel()`이 비어 있어 사용자 기능으로 노출하면 안 됨 |
| POST | `/internal/v1/payments/orders/{orderId}/refund` | Order 서비스가 호출하는 전액 환불 경로 |

`confirm` 요청 본문은 아래와 같다.

```json
{
  "paymentKey": "토스 결제키",
  "pgOrderId": "토스 주문번호",
  "amount": 1000
}
```

현재 Payment 서비스 자체는 사용자 소유권을 검증하지 않는다. 게이트웨이에서 JWT 인증을 적용하더라도, 이 서비스의 단건 조회·취소에는 `userId` 비교가 구현되어 있지 않다.

## 5. 결제 준비 흐름

1. Order 서비스가 `orderId`, `amount`로 `/internal/v1/payments/prepare`를 호출한다.
2. 같은 `orderId` 결제가 있으면 금액이 같은지 확인한다.
3. 같고 기존 상태가 `READY`면 기존 결제 정보를 반환한다. `CONFIRMING`, `PAID`, `FAILED`, `CANCELLED` 상태면 각각의 상태에 맞는 예외를 반환한다.
4. 새 요청이면 Payment가 `pgOrderId`를 생성하고 `READY` 상태의 `Payment`를 저장한다. 이때 승인용 멱등키를 UUID로 생성한다.
5. `PaymentPreparationInfo`를 통해 `paymentId`, `pgOrderId`, `amount`, `status`를 반환한다. Order 또는 프론트엔드는 이 `pgOrderId`를 토스 결제창의 `orderId`로 사용한다.

## 6. 승인 흐름

1. 토스 결제창은 `paymentKey`, `orderId`, `amount`를 성공 URL에 전달한다.
2. 성공 페이지는 `/api/v1/payments/confirm`을 호출한다.
3. `PaymentConfirmationService`는 `pgOrderId`로 준비된 결제를 찾고, 요청 금액이 준비 금액과 같은지 확인한다.
4. 결제를 `CONFIRMING`으로 바꾸고 `paymentKey`를 저장한 뒤 트랜잭션을 종료한다. `paymentKey`와 승인 멱등키는 DB 저장 시 AES-256-GCM으로 암호화된다. 외부 PG 호출 중 DB 트랜잭션을 유지하지 않는다.
5. `PaymentService`는 조회 시 자동 복호화된 `paymentKey`, `pgOrderId`, 금액, 승인 멱등키를 `PaymentGateway`에 전달한다.
6. Toss 구현체는 `POST /v1/payments/confirm`을 호출하고, 응답 상태가 `DONE`인지 확인한다.
7. 토스 응답의 결제키·주문번호·금액이 내부 값과 같으면 결제를 `PAID`로 변경하고 `confirmedAt`을 저장한다.
8. `PaymentService`는 `PAID` 결과를 Order 서비스에 통보한다. Order 서비스 장애 시 Payment는 이미 `PAID`로 저장되며, 현재 동기 호출 실패를 영속 재시도하지 않는다.

### 동시 승인 처리

`Payment.version`의 낙관적 락으로 동시에 두 요청이 `READY → CONFIRMING`을 시도해도 하나만 저장에 성공한다. 충돌한 요청의 `OptimisticLockingFailureException`은 `PaymentConfirmationInProgressException`으로 변환되며, 공통 예외 처리기를 통해 `409 Conflict`를 반환한다.

이미 `CONFIRMING` 상태인 결제는 승인 중 예외를 반환하며, 이미 `PAID`인 결제를 다시 승인해도 기존 결과를 반환하지 않고 예외를 반환한다.

## 7. PG 구현체와 설정

`payment.gateway` 설정값으로 구현체를 선택한다.

| 설정값 | 구현체 | 용도 |
| --- | --- | --- |
| 없거나 `fake` | `FakePaymentGateway` | 로컬·테스트용. `payment.demo.delay-ms`로 응답 지연을 흉내 낼 수 있음 |
| `toss` | `TossPaymentGateway` | 토스 승인 API 실제 호출 |

토스 사용 시 아래 설정이 필요하다.

```text
PAYMENT_GATEWAY=toss
PAYMENT_TOSS_SECRET_KEY=test_sk_... 또는 live_sk_...
PAYMENT_SECURITY_ENCRYPTION_KEY=Base64로_인코딩한_32바이트_AES_키
```

`TossPaymentConfig`는 시크릿 키와 빈 비밀번호를 Basic 인증으로 설정한다. `TossPaymentGateway`는 승인 시 `POST /v1/payments/confirm`을, 복구 조회 시 `GET /v1/payments/{paymentKey}`를 호출한다. `TossRefundGateway`는 전액 환불 시 `POST /v1/payments/{paymentKey}/cancel`을 호출하고 응답 상태가 `CANCELED`인지 검증한다. 클라이언트 키는 결제창을 여는 프론트엔드용 키이며 시크릿 키와 같은 상점의 키 쌍이어야 한다.

토스 4xx/5xx 응답과 네트워크 오류는 `TossPaymentException`으로 변환한다.

`FakePaymentGateway.getPayment()`는 `UnsupportedOperationException`을 던지며 결제 조회를 지원하지 않는다. 그런데 웹훅 정합화(`TossWebhookController`)와 복구 배치(`PaymentRecoveryService`)는 모두 `PaymentGateway.getPayment()`를 호출하므로, `payment.gateway=fake`(기본값)로 실행 중인 환경에서는 웹훅 처리와 복구 배치가 예외를 던지며 동작하지 않는다. 로컬에서 이 두 기능을 확인하려면 `toss` 구현체를 사용하거나 별도의 Fake 조회 지원이 필요하다.

## 8. 멱등성의 현재 범위

서버는 결제 준비 시 `approveIdempotencyKey`를 한 번 생성해 DB에 저장한다. 이후 토스 승인 호출의 `Idempotency-Key` 헤더에 같은 값을 사용한다.

따라서 동일 승인 시도에 대한 토스 API 중복 호출 방지에는 사용된다. 다만 내부 API가 “이미 완료된 동일 요청에 기존 성공 결과를 반환”하는 수준의 완전한 API 멱등성을 제공하지는 않는다.

## 9. 취소 및 환불 현황

기존 `PaymentService.cancel()`은 `TossPaymentGateway.cancel()`을 호출하지만 해당 구현체는 비어 있다. 따라서 기존 공개 취소 API는 실제 Toss 결제를 취소하지 못하므로 사용자 기능으로 노출하면 안 된다.

새 환불 경로는 `RefundService.refund(orderId, reason)`이 담당한다.

```text
PAID Payment 조회
  → Refund를 REQUESTED로 저장
  → TossRefundGateway가 Toss 취소 API 호출
  → Toss 응답이 CANCELED이면 Refund COMPLETED, Payment CANCELLED
```

- 환불 금액은 외부 요청에서 받지 않고 저장된 `Payment.amount`를 사용한다.
- 현재는 전액 환불만 지원한다. 부분 취소 금액은 전달하지 않는다.
- `RefundService`의 트랜잭션 안에서 외부 Toss 호출이 실행된다. 네트워크 오류 후 `REQUESTED` Refund 재조회·재시도는 아직 구현되지 않았다.
- Toss 관리자 화면에서 직접 취소된 결제는 웹훅으로 조회되더라도 현재 `PAID → CANCELLED` 정합화와 Refund 생성이 되지 않는다.

## 10. 타임아웃 및 승인 결과 복구

토스 승인 요청 후 네트워크 오류·응답 유실이 발생하면, 토스가 실제로 승인했는지 즉시 알 수 없다. 이 경우 Payment를 바로 `FAILED`로 바꾸지 않고 `CONFIRMING` 상태로 유지한다.

`PaymentRecoveryScheduler`는 기본 3분(`payment.recovery.schedule-fixed-delay=180000`) 간격으로 `PaymentRecoveryBatchService`를 호출한다. `PaymentRecoveryProperties`는 다음 값을 제공하며 각 Duration의 최소값만 검증한다. `maximumConfirmingDuration >= confirmationTimeOut` 관계 검증은 이 브랜치에 아직 반영되지 않았다.

| 설정 | 기본값 | 용도 |
| --- | --- | --- |
| `confirmationTimeOut` | `PT3M` | 복구 대상이 되는 `CONFIRMING` 경과 시간 |
| `batchSize` | `100` | 한 주기에 조회할 최대 결제 수 |
| `maximumConfirmingDuration` | `PT10M` | `PENDING`을 계속 기다리지 않고 `FAILED` 처리하는 최대 시간 |

배치 서비스는 다음 기준으로 최대 100건의 결제 ID만 조회한 뒤 순차 처리한다.

```text
현재 시각 - confirmationTimeOut(기본 3분)
보다 오래된 CONFIRMING 결제
```

각 결제는 저장된 `paymentKey`로 토스 `GET /v1/payments/{paymentKey}`를 조회한다.

| Toss 상태 | 내부 처리 |
| --- | --- |
| `DONE` | 응답의 결제키·주문번호·금액 검증 후 `PAID` |
| `ABORTED` | `FAILED` |
| `EXPIRED` | `FAILED` |
| `CANCELED` | `FAILED` |
| `READY`, `IN_PROGRESS` 등 `PENDING`으로 매핑되는 상태 | 최대 `CONFIRMING` 대기 시간을 넘기기 전까지 유지, 초과 시 `FAILED` |

한 건의 조회·복구가 실패해도 예외를 로그로 남기고 다음 결제를 계속 처리한다. 현재는 단일 인스턴스 기준이며, Payment 서비스를 다중 인스턴스로 운영할 경우 동일 스케줄 작업의 중복 실행을 막기 위한 분산 락(ShedLock 등)이 필요하다.

`TossPaymentConfig`의 `RestClient`에는 connect/read timeout이 아직 설정되어 있지 않다. 이는 별도 HTTP 클라이언트 정책 작업으로 보완한다.

## 11. Toss 웹훅 및 결제 정합화

Toss 웹훅은 Gateway를 통해 `POST /api/v1/payments/webhook`으로 수신한다. 외부 요청 본문에는 `eventType`, `data.paymentKey`를 받지만, 상태·금액·주문번호는 웹훅 본문을 신뢰하지 않는다. `paymentKey`로 Toss `GET /v1/payments/{paymentKey}`를 다시 호출한 결과를 사용한다.

```text
Toss 웹훅 또는 복구 배치
  → Toss 결제 조회
  → PaymentConfirmationService.reconcile()
  → PaymentReconciliationService
  → OrderStatusPort.notifyStatus() (PAID일 때)
```

`PaymentConfirmationService.reconcile()`은 트랜잭션 안에서 Toss 응답의 `pgOrderId`로 Payment를 조회한 뒤, 복호화된 `paymentKey`가 응답의 결제 키와 같은지 검증하고 상태를 전이한다. 완료 상태는 `PaymentInfo`를 반환하고, `PaymentReconciliationService`가 트랜잭션 밖에서 Order 서비스에 `PAID` 상태를 통보한다.

- 이미 `PAID`인 완료 웹훅은 Payment를 다시 저장하지 않는다.
- 다만 `PAID` 정보를 다시 반환하므로 웹훅 재전송 또는 복구 재실행 시 Order 상태 통보는 다시 시도한다.
- Order 통보가 실패하면 Payment 상태는 이미 커밋된 `PAID`로 남는다. 현재는 웹훅 재전송에 기대며, 완전한 전달 보장은 outbox·재시도 작업이 필요하다.

웹훅 URL은 Toss가 접근 가능한 HTTPS 공개 주소로 등록해야 한다. 로컬 E2E 테스트에서는 Gateway를 향하는 터널을 사용한다. 결제 상태 변경 웹훅의 출처 제어는 현재 애플리케이션 서명 검증이 아니라 Toss 재조회와 인프라 ACL에 의존한다.

## 12. 테스트

- `PaymentTest`: 상태 전이와 입력값 검증
- `PaymentServiceTest`: 준비·금액 검증·승인 결과 검증·Fake PG 연동
- `PaymentConfirmationServiceConcurrencyTest`: MySQL Testcontainers에서 낙관적 락 기반 동시 승인 선점 검증
- `FakePaymentGatewayTest`: Fake PG의 멱등 응답 검증
- `PaymentRecoveryServiceTest`: Toss 조회 상태별 완료·실패·대기 복구 분기 검증
- `PaymentRecoveryBatchServiceTest`: 타임아웃 조회·배치 크기·개별 실패 격리 검증
- `PaymentRecoverySchedulerTest`: 스케줄러의 배치 서비스 호출 검증
- `PaymentConfirmationServiceReconcileTest`: 완료·취소 상태 정합화와 중복 완료 웹훅 처리 검증
- `PaymentReconciliationServiceTest`: `PAID` 정합화 후 Order 상태 통보, 미전이 시 미통보 검증
- `TossWebhookControllerTest`: 웹훅 `paymentKey` 기반 Toss 조회·정합화 서비스 위임 검증
- `RefundServiceTest`: 전액 환불 완료, PAID가 아닌 결제 거절, Toss 환불 실패 시 Payment 상태 유지 검증
- `PaymentSensitiveDataCryptoTest`: AES-GCM 암·복호화, 랜덤 IV로 인한 암호문 비결정성, 암호문 변조 감지 검증
- `PaymentSensitiveDataConverterTest`: JPA Converter의 DB 저장 암호화·엔티티 조회 복호화 검증
- `PaymentConfirmationServiceConcurrencyTest`: Testcontainers MySQL 테스트 슬라이스에 암호화 키·AES-GCM Bean을 등록해 실제 암호화 컬럼과 낙관적 락 동시성 검증

현재 테스트는 실제 토스 네트워크를 호출하지 않는다. `TossPaymentGateway`의 HTTP 응답 매핑과 오류 변환은 별도 단위 테스트가 필요하다.

## 13. 다음 우선순위

1. Order 상태 통보 실패를 보장성 있게 재시도할 outbox 또는 별도 재시도 구조 구현
2. 웹훅 `eventType` 검증 및 Toss 인바운드 IP ACL 적용
3. `READY` 상태 장기 체류 결제의 만료 정책 수립 및 구현
4. 내부 환불 Controller 테스트 추가
5. 기존 공개 취소 API를 환불 경로로 통합하거나 제거
6. 환불 `REQUESTED` 상태의 재조회·재시도 정책 구현
7. 공통 HTTP client timeout 설정 및 토스별 timeout 정책 추가
8. 다중 인스턴스 운영 시 스케줄러 분산 락 적용
9. 사용자 소유권 검증 및 내 결제 내역 API
10. 승인·취소 이벤트와 Settlement 환불 연동

## 14. 복구 및 정합화 정책의 남은 과제

`CONFIRMING`으로 전이된 시각인 `confirmingAt`을 기준으로 복구 정책을 구현했다.

- `confirmingAt` 이후 3분이 지나면 Toss 상태 조회 대상에 포함한다.
- Toss 조회 결과가 계속 `PENDING`이고 `confirmingAt` 이후 10분이 지나면 `FAILED`로 전이한다.
- 배치 실행 주기, 복구 조회 시작 시간, 최종 대기 시간을 설정 파일에 명시하고 각 값의 역할을 분리한다.
- `maximumConfirmingDuration`과 `confirmationTimeOut`의 관계 검증은 후속 보완이 필요하다.

## 15. Order 서비스 환불 연동 계약

주문 취소는 Order 서비스가 시작하고, Payment 서비스는 결제 취소(전액 환불)만 처리한다. 따라서 Payment 서비스가 환불 완료 후 Order 서비스에 별도로 취소 상태를 통보하지 않는다. Order 서비스가 Payment 서비스의 환불 성공 응답을 받은 뒤 주문을 `CANCELLED`로 전이하고 재고 복구 등 주문 후속 처리를 수행한다.

내부 환불 API는 다음 계약으로 추가한다.

```text
POST /internal/v1/payments/orders/{orderId}/refund

PathVariable
  - orderId: Long

Request body
  - reason: USER_CANCEL | GOAL_FAILED

Response body
  - refundId
  - paymentId
  - amount
  - status: COMPLETED
```

- Order 서비스는 주문 소유권, 현재 주문 상태, 취소 가능 여부를 먼저 검증한 뒤 이 API를 호출한다.
- 환불 금액은 Order 서비스가 전달하지 않는다. Payment 서비스가 저장된 `Payment.amount`를 사용해 전액 환불하므로, 요청 금액 위변조를 막을 수 있다.
- Payment 서비스는 해당 주문의 `PAID` 결제만 환불한다. Toss 취소 성공 후 `Refund`를 `COMPLETED`, `Payment`를 `CANCELLED`로 변경하고 성공 응답을 반환한다.
- Payment 서비스가 실패 응답을 반환하면 Order 서비스는 주문 취소·재고 복구를 수행하지 않는다. 현재 범위는 해피 케이스이며, 네트워크 오류 후 환불 결과 재조회·재시도 정책은 후속 작업으로 다룬다.
