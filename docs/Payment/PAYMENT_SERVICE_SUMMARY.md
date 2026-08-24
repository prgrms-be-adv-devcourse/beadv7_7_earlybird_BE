# Payment 서비스 기술 선택 요약

## 1. 책임과 결제 흐름

Payment 서비스는 결제 준비, Toss 승인·취소, 결제 상태 전이, 승인 결과 복구, 결제 상태 이벤트 발행을 담당한다.

`pgOrderId`와 `paymentKey`의 소유자는 Payment 서비스다. Order 서비스는 주문을 만들고 결제 준비를 요청하며, FE는 Payment 서비스에서 준비 결제를 조회해 Toss 결제창을 연다.

```text
FE → Order: 주문 생성
Order → Payment: prepare(userId, orderId, amount)
Payment: READY Payment 생성, pgOrderId 생성

FE → Payment: GET /api/v1/payments/orders/{orderId}
FE → Toss SDK: requestPayment(pgOrderId, amount)
Toss → FE successUrl: paymentKey, orderId(pgOrderId), amount
FE → Payment: confirm(paymentKey, pgOrderId, amount)
Payment → Toss: 승인 API 호출
Payment: PAID + PaymentStatusOutbox 저장
Outbox Dispatcher → Kafka: payment.single-result.v1 발행
Order → Kafka 수신: 주문 결제 상태 반영
```

Payment은 생성 시 전달받은 `userId`를 저장한다. 결제 승인·단건 조회·주문 조회·사용자 취소·환불 요청에서 JWT 요청자와 Payment 소유자를 검증한다.

## 2. 상태 전이와 동시성 제어

```text
READY → CONFIRMING → PAID → CANCELLED
  └→ FAILED          └→ FAILED
FAILED ──(Toss 정합화 결과 DONE)──→ PAID
```

### 선택: `@Version` 낙관적 락으로 승인 선점

두 confirm 요청이 동시에 들어와도 `READY → CONFIRMING` 전이는 하나만 성공한다. 충돌한 요청은 `409 Conflict`로 반환한다.

| 항목 | 내용 |
| --- | --- |
| 해결하는 문제 | 중복 클릭·네트워크 재전송으로 인한 중복 Toss 승인 |
| 장점 | DB 락을 오래 점유하지 않고, 일반적인 저경합 결제 승인에 단순하게 적용 가능 |
| 트레이드오프 | 충돌 요청은 재시도 대신 409을 받으며, 클라이언트는 승인 진행 중 상태를 처리해야 함 |
| 대체재 | 비관적 락, Redis 분산 락 |
| 미선택 이유 | 단일 Payment 행의 짧은 상태 전이라 별도 락 인프라보다 JPA `@Version`이 단순함 |

### 선택: 서버 생성 승인 멱등키

`Payment` 생성 시 승인용 UUID를 만들고 Toss 승인 요청의 `Idempotency-Key` 헤더로 보낸다.

| 항목 | 내용 |
| --- | --- |
| 해결하는 문제 | 응답 유실 뒤 승인 요청을 재시도할 때 PG의 중복 승인 가능성 |
| 장점 | 동일 결제의 재시도가 Toss에서 같은 승인 요청으로 식별됨 |
| 트레이드오프 | 키를 Payment와 함께 안전하게 보관해야 하며, 키 재사용 범위를 결제 단위로 제한해야 함 |
| 대체재 | FE가 멱등키 생성, PG 멱등성 없이 자체 중복 처리 |
| 미선택 이유 | PG 승인 호출 주체가 Payment 서비스이므로 서버가 키 수명주기를 관리하는 편이 일관됨 |

## 3. Toss 연동과 Saga

### 선택: DB 상태 전이와 Toss 호출을 분리한 Saga

승인 전 `CONFIRMING` 상태를 먼저 저장하고 트랜잭션을 종료한다. 그 뒤 Toss API를 호출하고, 응답 검증 후 별도 트랜잭션에서 `PAID` 또는 `FAILED`로 반영한다.

| 항목 | 내용 |
| --- | --- |
| 해결하는 문제 | 외부 PG 네트워크 호출 동안 DB 트랜잭션·락을 점유하는 문제 |
| 장점 | PG 지연이 DB 연결을 막지 않으며, 중간 상태를 남겨 복구할 수 있음 |
| 트레이드오프 | `CONFIRMING` 같은 중간 상태와 복구 로직이 필요함 |
| 대체재 | 하나의 DB 트랜잭션 안에서 Toss 호출 |
| 미선택 이유 | 외부 호출 시간은 통제할 수 없고, DB 트랜잭션을 길게 유지하면 장애 전파 범위가 커짐 |

Toss 결제창 호출은 FE 책임이다. `paymentKey`는 결제창 호출 뒤 Toss가 success URL로 반환하며, Payment 서비스는 이를 받아 실제 승인 API를 호출한다.

## 4. Outbox와 Kafka 상태 전파

### 선택: Transactional Outbox + Kafka

`PAID`, `FAILED`, `CANCELLED` 상태 전이와 `PaymentStatusOutbox(PENDING)` 저장을 같은 DB 트랜잭션으로 처리한다. 커밋 후 즉시 Dispatcher가 `payment.single-result.v1`에 이벤트를 발행하고, 실패한 건은 기본 60초 주기의 배치가 재시도한다.

Dispatcher는 발행 전 `PENDING → PROCESSING` 원자적 선점을 수행한다. 즉시 발행과 배치가 같은 Outbox를 동시에 읽어도 하나만 발행하며, 5분 이상 `PROCESSING`에 머문 Outbox는 다시 `PENDING`으로 복구한다. Kafka 발행 성공 후 `SENT` 저장 전 프로세스 장애가 나면 재발행될 수 있으므로, 소비자는 at-least-once 전달을 전제로 멱등 처리해야 한다.

```json
{
  "orderId": 4,
  "pgOrderId": "order-4-...",
  "status": "PAID"
}
```

| 항목 | 내용 |
| --- | --- |
| 해결하는 문제 | 결제 DB 반영과 서비스 간 이벤트 발행의 dual-write 불일치 |
| 장점 | Kafka가 일시 장애여도 Outbox가 남아 재발행 가능, Order와 동기 HTTP 결합 제거 |
| 트레이드오프 | 즉시 일관성이 아닌 최종 일관성, Outbox 테이블·Scheduler 운영 필요 |
| 대체재 | Payment → Order 동기 Feign 호출, DB 저장 뒤 Kafka 직접 발행 |
| 미선택 이유 | Feign은 Order 장애가 Payment 승인 응답에 전파되고, 직접 발행은 DB 성공·Kafka 실패 시 이벤트 유실 가능 |

Kafka key는 `String.valueOf(orderId)`다. 같은 주문 이벤트가 같은 파티션으로 가므로 순서가 유지된다. 공용 Kafka 설정이 String key 직렬화를 사용하므로, `Long` 키 제네릭으로만 바꾸지 않고 현재 규약을 유지한다.

## 5. 재시도, Circuit Breaker, 정합화 복구

### 선택: Retry + Circuit Breaker + Recovery Batch

Toss 승인·조회는 네트워크 오류처럼 결과가 불확실한 실패와 4xx 같은 확정 실패를 구분한다. 불확실한 경우 즉시 실패 확정하지 않고, 웹훅과 Recovery Batch가 Toss 결제 상태를 다시 조회해 정합화한다.

| 항목 | 내용 |
| --- | --- |
| 해결하는 문제 | Toss 승인 결과는 성공했지만 응답을 받지 못한 경우 |
| 장점 | 실제 PG 상태를 기준으로 PAID를 복구해 잘못된 실패 처리를 줄임 |
| 트레이드오프 | 최종 상태 확정이 지연될 수 있고, 배치·웹훅·정합화 경로를 함께 유지해야 함 |
| 대체재 | 오류 시 즉시 FAILED 처리, 운영자 수동 복구 |
| 미선택 이유 | 승인 결과 유실을 FAILED로 단정하면 PG에서는 결제됐지만 서비스에서는 실패인 불일치가 발생함 |

## 6. 민감 결제 데이터 암호화

### 선택: JPA AttributeConverter 기반 AES-256-GCM 암호화

`paymentKey`, `approveIdempotencyKey`, `cancelIdempotencyKey`는 DB 저장 시 AES-256-GCM으로 암호화하고, 엔티티 조회 시 자동 복호화한다. 키는 소스가 아닌 `PAYMENT_SECURITY_ENCRYPTION_KEY` 환경변수로 관리한다.

| 항목 | 내용 |
| --- | --- |
| 해결하는 문제 | DB 덤프·조회 권한 유출 시 결제 식별자 노출 |
| 장점 | 도메인 코드가 암호화 호출을 반복하지 않으며, 같은 평문도 매번 다른 암호문으로 저장 |
| 트레이드오프 | 암호문 동등 검색이 불가능하고, 암호화 키 교체·복구 전략이 필요 |
| 대체재 | 평문 저장, 애플리케이션 서비스에서 수동 암호화, KMS/Vault Envelope Encryption |
| 미선택 이유 | 현재 규모에서는 JPA Converter가 추가 인프라 없이 저장 경계를 일관되게 보호함 |

## 6-1. 암호화 적용 방식

### 적용 방식: `SensitiveValue`와 `autoApply = true`

암호화 대상은 `SensitiveValue` VO로 선언하고, `AttributeConverter<SensitiveValue, String>`에 `@Converter(autoApply = true)`를 적용한다. 따라서 일반 `String`에는 영향을 주지 않으며, 엔티티가 Infrastructure Converter 구현체를 직접 참조하지 않는다.

대안과 트레이드오프는 [ADR-002: Payment 민감값의 JPA 자동 암복호화](adr/ADR-002-payment-sensitive-value-conversion.md)에 기록한다.

## 7. 테스트 전략

| 구분 | 선택 | 검증 대상 |
| --- | --- | --- |
| 단위 테스트 | In-memory Repository, Mockito | 상태 전이, Saga 분기, Outbox 발행 성공·실패 |
| 통합 테스트 | Testcontainers MySQL | 낙관적 락 기반 동시 승인 선점 |
| Kafka 확인 | 로컬 Kafka 브로커·Kafka UI | 실제 `payment.single-result.v1` 레코드 발행 |

단위 테스트만으로는 JPA 낙관적 락과 실제 Kafka 직렬화·브로커 전달을 확인할 수 없으므로, 경계가 중요한 구간에만 통합 검증을 사용한다.

## 8. 현재 한계와 다음 개선점
