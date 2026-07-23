# Payment 도메인 구현 현황

담당자: @[단기심화7] 정창민  
기준일: 2026-07-22

이 문서는 목표 설계가 아니라 현재 `payment-service` 코드의 구현 상태를 기준으로 작성한다.

## 1. 목적

Payment 도메인은 주문에 대한 결제 준비, PG 승인 요청, 결제 상태 관리, 결제 조회를 담당한다. 현재 PG 구현체는 테스트용 `FakePaymentGateway`와 토스페이먼츠용 `TossPaymentGateway`를 지원한다.

실제 승인 흐름에서는 준비 단계에서 저장한 주문 번호·사용자·금액을 기준으로 요청을 검증하고, 토스 승인 성공 후에만 내부 결제를 `PAID`로 변경한다.

## 2. 현재 책임 범위

### 구현됨

- 내부 결제 준비: 주문 ID, 사용자 ID, PG 주문번호, 금액을 `READY` 결제로 저장
- 토스 결제창 인증 후 승인 요청 처리
- 준비된 금액과 승인 요청 금액 비교
- 토스 승인 응답의 주문번호·결제키·금액 검증
- 결제 단건 조회
- `@Version` 기반 낙관적 락으로 동시 승인 선점 보호
- 승인용 멱등키 생성 및 토스 `Idempotency-Key` 헤더 전달
- Fake PG 및 Toss PG 구현체 선택

### 아직 구현되지 않음 또는 미완성

- 사용자 소유권 검증 및 내 결제 내역 조회(페이징·상태 필터)
- 토스 웹훅 수신, 서명 검증, PG 상태 동기화
- 승인/취소 이벤트 발행
- Settlement 이벤트 기반 일괄 환불
- 토스 취소 API 호출
- 프로젝트 마감 전 취소 가능 여부 검증
- PG 타임아웃의 최종 상태 복구 및 재조정
- 카드·영수증 등 토스 상세 응답 저장, 암호화·마스킹

## 3. 도메인 모델

`Payment` 엔티티는 `payments` 테이블을 사용한다.

| 필드 | 의미 |
| --- | --- |
| `paymentId` | 내부 결제 식별자. DB 컬럼은 `id` |
| `orderId` | 주문 서비스의 주문 식별자. 결제당 하나이며 유니크 |
| `userId` | 결제 요청 사용자 식별자 |
| `pgOrderId` | 토스 결제창에 전달하는 주문번호. 유니크 |
| `paymentKey` | 토스가 인증 성공 후 발급하는 결제 식별자. 승인 성공 후 저장 |
| `amount` | 준비·승인 단계에서 검증하는 결제 금액 |
| `approveIdempotencyKey` | 승인 재시도 시 토스에 전달하는 서버 생성 UUID. 유니크 |
| `version` | 낙관적 락 버전 |
| `confirmingAt` | 승인 선점 시각 |
| `confirmedAt` | 내부 승인 완료 시각 |
| `canceledAt` | 내부 취소 처리 시각 |
| `status` | 결제 상태 |

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
                   └→ FAILED
```

- `READY`: 결제 준비 완료, 아직 PG 승인 전
- `CONFIRMING`: 한 요청이 승인 처리를 선점한 상태
- `PAID`: PG 승인 검증 및 내부 저장 완료
- `FAILED`: `CONFIRMING` 상태의 실패 처리 메서드는 있으나 현재 승인 흐름에서 자동 호출하지 않음
- `CANCELLED`: 내부 취소 처리 완료 상태

## 4. API

| Method | Path | 현재 동작 |
| --- | --- | --- |
| POST | `/internal/v1/payments/prepare` | `READY` 결제를 생성하거나, 동일한 준비 요청이면 기존 결제를 반환 |
| POST | `/api/v1/payments/confirm` | 금액 검증 → PG 승인 → `PAID` 처리 |
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

1. 클라이언트 또는 내부 호출자가 `orderId`, `userId`, `pgOrderId`, `amount`로 `/internal/v1/payments/prepare`를 호출한다.
2. 같은 `orderId` 결제가 있으면 `pgOrderId`, `userId`, `amount`가 모두 같은지 확인한다.
3. 모두 같으면 기존 결제 정보를 반환한다.
4. 다르면 예외를 반환한다.
5. 새 요청이면 Payment가 `pgOrderId`를 생성하고 `READY` 상태의 `Payment`를 저장한다. 이때 승인용 멱등키를 UUID로 생성한다.
6. `PaymentPreparationInfo`를 통해 `paymentId`, `pgOrderId`, `amount`, `status`를 반환한다. Order 또는 프론트엔드는 이 `pgOrderId`를 토스 결제창의 `orderId`로 사용한다.

## 6. 승인 흐름

1. 토스 결제창은 `paymentKey`, `orderId`, `amount`를 성공 URL에 전달한다.
2. 성공 페이지는 `/api/v1/payments/confirm`을 호출한다.
3. `PaymentConfirmationService`는 `pgOrderId`로 준비된 결제를 찾고, 요청 금액이 준비 금액과 같은지 확인한다.
4. 결제를 `CONFIRMING`으로 바꾸고 트랜잭션을 종료한다. 외부 PG 호출 중 DB 트랜잭션을 유지하지 않는다.
5. `PaymentService`는 `PaymentGateway`에 `paymentKey`, `pgOrderId`, 금액, 저장된 멱등키를 전달한다.
6. Toss 구현체는 `POST /v1/payments/confirm`을 호출하고, 응답 상태가 `DONE`인지 확인한다.
7. 토스 응답의 결제키·주문번호·금액이 내부 값과 같으면 결제를 `PAID`로 변경하고 `paymentKey`, `confirmedAt`을 저장한다.

### 동시 승인 처리

`Payment.version`의 낙관적 락으로 동시에 두 요청이 `READY → CONFIRMING`을 시도해도 하나만 저장에 성공한다. 충돌한 요청의 `OptimisticLockingFailureException`은 `PaymentConfirmationInProgressException`으로 변환되며, 공통 예외 처리기를 통해 `409 Conflict`와 `C002`를 반환한다.

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

`TossPaymentConfig`는 시크릿 키와 빈 비밀번호를 Basic 인증으로 설정하고, `https://api.tosspayments.com/v1/payments/confirm`을 호출한다. 클라이언트 키는 결제창을 여는 프론트엔드용 키이며 시크릿 키와 같은 상점의 키 쌍이어야 한다.

토스 4xx/5xx 응답은 `TossPaymentException`으로 변환한다. 현재 4xx는 `C002`, 5xx 및 네트워크 오류는 `C503`으로 매핑한다.

## 8. 멱등성의 현재 범위

서버는 결제 준비 시 `approveIdempotencyKey`를 한 번 생성해 DB에 저장한다. 이후 토스 승인 호출의 `Idempotency-Key` 헤더에 같은 값을 사용한다.

따라서 동일 승인 시도에 대한 토스 API 중복 호출 방지에는 사용된다. 다만 내부 API가 “이미 완료된 동일 요청에 기존 성공 결과를 반환”하는 수준의 완전한 API 멱등성을 제공하지는 않는다.

## 9. 취소 현황

`PaymentService.cancel()`은 `PAID` 상태인지 확인한 뒤 `paymentGateway.cancel(paymentKey)`를 호출하고 내부 상태를 `CANCELLED`로 변경한다.

그러나 현재 `TossPaymentGateway.cancel()`은 비어 있다. 따라서 토스 PG를 사용하는 환경에서 취소 API를 공개하면 실제 토스 결제는 취소되지 않은 채 내부 상태만 바뀔 수 있다. 토스 취소 API, 취소 사유·금액, 토스 응답 검증이 구현되기 전에는 이 API를 사용자 기능으로 노출하면 안 된다.

## 10. 타임아웃 및 실패 복구 현황

`FakePaymentGateway`의 `payment.demo.delay-ms`는 지연을 재현하는 테스트용 설정이며 HTTP 타임아웃 설정이 아니다. `TossPaymentConfig`의 `RestClient`에는 connect/read timeout이 아직 설정되어 있지 않다.

토스 통신 오류는 `TossPaymentException(C503)`으로 응답하지만, 이 경우 결제는 `CONFIRMING`에 남을 수 있다. 토스가 실제로 승인했는지 알 수 없는 경우가 있으므로 즉시 `FAILED`로 변경하는 것은 안전하지 않다. 향후에는 토스 결제 조회 API, 웹훅 또는 재조정 작업으로 최종 상태를 확인해야 한다.

## 11. 테스트

- `PaymentTest`: 상태 전이와 입력값 검증
- `PaymentServiceTest`: 준비·금액 검증·승인 결과 검증·Fake PG 연동
- `PaymentConfirmationServiceConcurrencyTest`: MySQL Testcontainers에서 낙관적 락 기반 동시 승인 선점 검증
- `FakePaymentGatewayTest`: Fake PG의 멱등 응답 검증

현재 테스트는 실제 토스 네트워크를 호출하지 않는다. `TossPaymentGateway`의 HTTP 응답 매핑과 오류 변환은 별도 단위 테스트가 필요하다.

## 12. 다음 우선순위

1. `TossPaymentGateway.cancel()`과 취소 응답 검증 구현
2. 공통 HTTP client timeout 설정 및 토스별 timeout 정책 추가
3. `CONFIRMING` 장기 체류 건을 토스 조회/웹훅으로 복구
4. 웹훅 서명 검증과 상태 동기화
5. 사용자 소유권 검증 및 내 결제 내역 API
6. 승인·취소 이벤트와 Settlement 환불 연동
