# Payment 도메인 구현 현황

담당자: @[단기심화7] 정창민
기준일: 2026-07-24

이 문서는 목표 설계가 아니라 현재 `payment-service` 코드의 구현 상태를 기준으로 작성한다.

## 1. 목적

Payment 도메인은 주문에 대한 결제 준비, PG 승인 요청, 결제 상태 관리, 결제 조회와 승인 결과 복구를 담당한다. 현재 PG 구현체는 테스트용 `FakePaymentGateway`와 토스페이먼츠용 `TossPaymentGateway`를 지원한다.

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
- 오래된 `CONFIRMING` 결제의 토스 상태 복구
- `CONFIRMING` 최대 대기 시간 초과 시 `PENDING` 결제 실패 처리
- 3분 주기 타임아웃 결제 복구 배치 실행
- Toss `PAYMENT_STATUS_CHANGED` 웹훅 수신 및 Toss 재조회 기반 상태 정합화
- 정합화로 `PAID`가 된 결제의 Order 서비스 상태 통보
- `status`, `confirmingAt` 복합 인덱스를 사용한 복구 대상 조회
- Fake PG 및 Toss PG 구현체 선택

### 아직 구현되지 않음 또는 미완성

- 사용자 소유권 검증 및 내 결제 내역 조회(페이징·상태 필터)
- 승인/취소 이벤트 발행
- 토스 취소 API 호출
- 프로젝트 마감 전 취소 가능 여부 검증
- `READY` 상태로 장기 체류한 미결제 건의 만료 정책
- 카드·영수증 등 토스 상세 응답 저장, 암호화·마스킹
- 웹훅 `eventType` 검증 및 인프라 레벨 Toss 인바운드 IP ACL 적용

## 3. 도메인 모델

`Payment` 엔티티는 `payments` 테이블을 사용한다.

| 필드 | 의미 |
| --- | --- |
| `paymentId` | 내부 결제 식별자. DB 컬럼은 `id` |
| `orderId` | 주문 서비스의 주문 식별자. 현재는 `Long`이며 결제당 하나로 유니크. Order의 UUID 전환 시 함께 변경 필요 |
| `pgOrderId` | 토스 결제창에 전달하는 주문번호. 유니크 |
| `paymentKey` | 토스가 인증 성공 후 발급하는 결제 식별자. `READY → CONFIRMING` 전이 시 저장하며, 상태 복구 조회 키로 사용 |
| `amount` | 준비·승인 단계에서 검증하는 결제 금액 |
| `approveIdempotencyKey` | 승인 재시도 시 토스에 전달하는 서버 생성 UUID. 유니크 |
| `version` | 낙관적 락 버전 |
| `confirmingAt` | 승인 선점 시각 |
| `confirmedAt` | 내부 승인 완료 시각 |
| `canceledAt` | 내부 취소 처리 시각 |
| `status` | 결제 상태 |

복구 배치 조회를 위해 `payments(status, confirming_At)` 복합 인덱스를 선언한다. 조회 조건인 `status = CONFIRMING`, `confirmingAt < cutoff` 및 정렬에 사용한다.

### 응답 DTO 분리

결제 정보는 용도에 따라 `PaymentInfo`와 `PaymentPreparationInfo`로 나눈다.

| DTO | 사용처 | 포함 정보 | 분리 이유 |
| --- | --- | --- | --- |
| `PaymentInfo` | 승인 결과, 결제 단건 조회, 취소 결과 | `paymentId`, `orderId`, `amount`, `status` | 일반 결제 상태 조회에 필요한 내부 결제 정보만 전달 |
| `PaymentPreparationInfo` | Order의 결제 준비 요청, 토스 결제창 호출 직전 | `paymentId`, `pgOrderId`, `amount`, `status` | 토스 결제창의 `orderId`로 사용할 `pgOrderId`를 전달 |

`pgOrderId`는 Payment가 생성·저장하며, 토스 인증 전 결제창을 열 때만 필수다. 이를 모든 승인·조회 응답에 포함하면 사용처와 무관한 값을 계속 노출하게 되므로, prepare 전용 DTO로 분리한다.

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
| POST | `/api/v1/payments/{paymentId}/cancel` | 내부 상태 변경 경로는 있으나 토스 취소 호출은 아직 비어 있음 |

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
4. 결제를 `CONFIRMING`으로 바꾸고 `paymentKey`를 저장한 뒤 트랜잭션을 종료한다. 외부 PG 호출 중 DB 트랜잭션을 유지하지 않는다.
5. `PaymentService`는 `PaymentGateway`에 `paymentKey`, `pgOrderId`, 금액, 저장된 멱등키를 전달한다.
6. Toss 구현체는 `POST /v1/payments/confirm`을 호출하고, 응답 상태가 `DONE`인지 확인한다.
7. 토스 응답의 결제키·주문번호·금액이 내부 값과 같으면 결제를 `PAID`로 변경하고 `confirmedAt`을 저장한다.
8. `PaymentService`는 `PAID` 결과를 Order 서비스에 통보한다.

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
```

`TossPaymentConfig`는 시크릿 키와 빈 비밀번호를 Basic 인증으로 설정한다. `TossPaymentGateway`는 승인 시 `POST /v1/payments/confirm`을, 복구 조회 시 `GET /v1/payments/{paymentKey}`를 호출한다. 클라이언트 키는 결제창을 여는 프론트엔드용 키이며 시크릿 키와 같은 상점의 키 쌍이어야 한다.

토스 4xx/5xx 응답과 네트워크 오류는 `TossPaymentException`으로 변환한다.

`FakePaymentGateway.getPayment()`는 `UnsupportedOperationException`을 던지며 결제 조회를 지원하지 않는다. 그런데 웹훅 정합화(`TossWebhookController`)와 복구 배치(`PaymentRecoveryService`)는 모두 `PaymentGateway.getPayment()`를 호출하므로, `payment.gateway=fake`(기본값)로 실행 중인 환경에서는 웹훅 처리와 복구 배치가 예외를 던지며 동작하지 않는다. 로컬에서 이 두 기능을 확인하려면 `toss` 구현체를 사용하거나 별도의 Fake 조회 지원이 필요하다.

## 8. 멱등성의 현재 범위

서버는 결제 준비 시 `approveIdempotencyKey`를 한 번 생성해 DB에 저장한다. 이후 토스 승인 호출의 `Idempotency-Key` 헤더에 같은 값을 사용한다.

따라서 동일 승인 시도에 대한 토스 API 중복 호출 방지에는 사용된다. 다만 내부 API가 “이미 완료된 동일 요청에 기존 성공 결과를 반환”하는 수준의 완전한 API 멱등성을 제공하지는 않는다.

## 9. 취소 현황

`PaymentService.cancel()`은 `paymentGateway.cancel(paymentKey)`를 먼저 호출한 뒤 `payment.cancel()`을 호출하며, `PAID` 상태 검증은 이 `payment.cancel()` 내부에서 이루어진다. 즉 상태 검증이 PG 취소 호출보다 먼저 일어나지 않는다. 지금은 `TossPaymentGateway.cancel()`이 빈 구현이라 실제 영향이 없지만, PG 취소 API가 구현되면 `PAID`가 아닌 결제에 대해서도 PG 취소 호출이 먼저 나간 뒤에야 예외가 발생하는 순서 문제가 생긴다. 취소 API를 실제로 노출하기 전에 상태 검증 → PG 호출 순서로 바로잡아야 한다.

또한 현재 `TossPaymentGateway.cancel()`은 비어 있다. 따라서 토스 PG를 사용하는 환경에서 취소 API를 공개하면 실제 토스 결제는 취소되지 않은 채 내부 상태만 바뀔 수 있다. 토스 취소 API, 취소 사유·금액, 토스 응답 검증이 구현되기 전에는 이 API를 사용자 기능으로 노출하면 안 된다.

## 10. 타임아웃 및 승인 결과 복구

토스 승인 요청 후 네트워크 오류·응답 유실이 발생하면, 토스가 실제로 승인했는지 즉시 알 수 없다. 이 경우 Payment를 바로 `FAILED`로 바꾸지 않고 `CONFIRMING` 상태로 유지한다.

`PaymentRecoveryScheduler`는 기본 3분(`payment.recovery.schedule-fixed-delay=180000`) 간격으로 `PaymentRecoveryBatchService`를 호출한다. `PaymentRecoveryProperties`는 다음 값을 검증하며 제공한다.

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

`PaymentConfirmationService.reconcile()`은 트랜잭션 안에서 Payment를 조회·검증·상태 전이한다. 완료 상태는 `PaymentInfo`를 반환하고, `PaymentReconciliationService`가 트랜잭션 밖에서 Order 서비스에 `PAID` 상태를 통보한다.

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

현재 테스트는 실제 토스 네트워크를 호출하지 않는다. `TossPaymentGateway`의 HTTP 응답 매핑과 오류 변환은 별도 단위 테스트가 필요하다.

## 13. 다음 우선순위

1. Order 상태 통보 실패를 보장성 있게 재시도할 outbox 또는 별도 재시도 구조 구현
2. 웹훅 `eventType` 검증 및 Toss 인바운드 IP ACL 적용
3. `READY` 상태 장기 체류 결제의 만료 정책 수립 및 구현
4. `TossPaymentGateway.cancel()`과 취소 응답 검증 구현
5. 공통 HTTP client timeout 설정 및 토스별 timeout 정책 추가
6. 다중 인스턴스 운영 시 스케줄러 분산 락 적용
7. 사용자 소유권 검증 및 내 결제 내역 API
8. 승인·취소 이벤트와 Settlement 환불 연동

## 14. Order ID UUID 전환 시 변경 사항

Order 서비스의 주문 식별자가 현재 `Long`에서 32자리 UUID 문자열로 변경되면, Payment도 동일한 주문 식별자 계약을 사용해야 한다. Order ID 형식이 확정된 뒤 아래 항목을 함께 변경한다.

- `Payment.orderId` 타입과 `Payment.ready()`·검증 메서드의 인자 타입
- `PaymentRepository.findByOrderId()` 및 JPA Repository 쿼리 메서드 타입
- `PaymentPreparationRequest`, `PaymentPreparationInfo`, 내부 Prepare API·Feign 계약
- Payment 테스트의 주문 ID fixture 및 인메모리 Repository의 키 타입

토스의 `orderId`는 최대 64자다. 현재의 생성 규칙인 `order-` + 내부 주문 ID + 32자리 랜덤 UUID는 주문 ID가 32자리 UUID가 되면 최대 71자가 되어 제한을 초과한다.

따라서 Order ID가 하이픈 없는 32자리 UUID 문자열로 확정되면, Payment의 `pgOrderId`는 별도 접두사·랜덤 UUID를 붙이지 않고 해당 `orderId`를 그대로 사용한다. 32자이므로 토스 제한 안에 들어오며 Payment의 `orderId` 유니크 제약과도 일치한다.

단, 주문당 여러 개의 서로 다른 토스 결제 시도를 생성해야 하는 요구사항이 생기면 `pgOrderId`는 재시도 식별자를 포함해 다시 설계해야 한다. 이 경우에도 토스 제한인 64자 이내를 보장해야 한다.
