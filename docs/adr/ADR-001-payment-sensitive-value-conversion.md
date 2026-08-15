# ADR-001: Payment 민감값의 JPA 자동 암복호화

- 상태: 승인됨
- 결정일: 2026-08-14
- 대상: `payment-service`

## 배경

`Payment.paymentKey`, `Payment.approveIdempotencyKey`, `Refund.cancelIdempotencyKey`는 AES-256 암복호화가 필요한 값이다.

기존에는 각 엔티티 필드에서 `@Convert(converter = PaymentSensitiveDataConverter.class)`를 선언했다. 이 방식은 JPA 변환 자체는 간단하지만, 도메인 엔티티가 infrastructure 패키지의 Converter 구현체를 직접 import하게 만든다.

```text
domain entity -> infrastructure PaymentSensitiveDataConverter
```

현재 `Payment`와 `Refund`는 상태 전이 로직과 JPA 매핑을 함께 가진 **JPA 엔티티 겸용 도메인 모델**이다. 따라서 이번 결정의 목표는 도메인을 완전히 순수화하는 것이 아니라, 암복호화 구현체를 직접 참조하는 의존성만 제거하는 것이다.

## 검토한 대안

### 1. 순수 도메인 모델과 JPA 엔티티를 분리한다

도메인 계층에는 JPA 어노테이션과 `BaseEntity` 상속이 없는 순수 `Payment` 모델을 둔다. infrastructure 계층에는 별도 JPA Entity와 Mapper를 두고, Repository Adapter가 두 모델을 변환한다.

장점:

- 도메인에서 JPA, `BaseEntity`, Converter를 모두 제거할 수 있다.
- 저장소 기술을 교체하거나 복잡한 도메인 규칙을 확장하기 쉽다.

단점:

- 엔티티와 도메인 모델, Mapper, Repository Adapter가 추가된다.
- 현재 엔티티의 상태 전이 로직을 이관하고 동기화해야 한다.
- 암복호화 의존성 제거라는 현재 범위에 비해 변경량과 회귀 위험이 크다.

이 방식은 도메인 규칙이 크게 복잡해지거나 영속성 기술 교체가 실제 요구가 될 때 재검토한다.

### 2. Application Port로 암복호화한다

application에 `SensitiveDataCryptoPort`를 두고 infrastructure에 AES-256 Adapter를 구현한다. Application Service가 저장 전 암호화하고, Toss PG 호출 전 복호화한다.

장점:

- 도메인 엔티티에서 JPA Converter를 제거할 수 있다.
- 암복호화 구현은 Port 뒤로 숨길 수 있다.

단점:

- 유스케이스마다 암호화·복호화 호출이 반복된다.
- 평문과 암호문이 모두 `String`이어서 Toss PG에 암호문을 전달하는 실수가 가능하다.
- 영속화·조회 시점의 변환 책임이 JPA가 아닌 application으로 이동한다.

현재처럼 JPA가 민감값의 저장과 조회를 담당하는 구조에는 적합하지 않다.

### 3. `SensitiveValue`와 `AttributeConverter` 자동 적용을 사용한다

암호화 대상만 `String` 대신 `SensitiveValue`로 선언한다. infrastructure의 `SensitiveValueConverter`가 `AttributeConverter<SensitiveValue, String>`을 구현하고 `@Converter(autoApply = true)`를 사용한다.

장점:

- 엔티티가 Converter 구현체를 import하거나 `@Convert`를 선언하지 않는다.
- JPA/Hibernate가 저장 시 암호화하고 조회 시 복호화한다.
- `String` 전체가 아니라 `SensitiveValue` 필드에만 적용된다.
- 기존 데이터베이스 컬럼은 `VARCHAR`이므로 스키마 변경이 필요 없다.
- 변경량이 작고 기존 상태 전이 로직을 유지한다.

단점:

- `Payment`와 `Refund`는 여전히 JPA 엔티티 겸용 모델이다.
- `BaseEntity`와 JPA 어노테이션 의존성은 남아 있다.
- `SensitiveValue`는 민감값 여부만 표현하며, payment key와 idempotency key를 컴파일 시점에 구분하지는 않는다.

## 결정

대안 3을 선택한다.

```text
Payment / Refund entity
        |
        +-- SensitiveValue
                  |
                  +-- SensitiveValueConverter (autoApply = true)
                            |
                            +-- PaymentSensitiveDataCrypto (AES-256/GCM)
```

`paymentKey`, `approveIdempotencyKey`, `cancelIdempotencyKey`에 `SensitiveValue`를 사용한다. Converter는 해당 타입의 필드만 암복호화하므로, 일반 `String` 컬럼에는 영향을 주지 않는다.

외부 PG 호출은 application 계층에서만 `SensitiveValue.value()`로 평문을 꺼내 전달한다. 이 경계 밖에서는 암호화 대상이 `SensitiveValue`로 유지된다.

## 결과와 재검토 기준

이 결정은 “순수 도메인”을 달성했다고 주장하지 않는다. JPA 엔티티 겸용 모델을 유지하는 현실적인 범위에서 infrastructure 구현체의 직접 참조를 제거한 점진적 개선이다.

다음 상황에서는 대안 1 또는 의미별 VO 분리를 재검토한다.

- payment key, 승인 멱등 키, 환불 멱등 키의 검증·마스킹·보존 정책이 달라질 때
- Port와 application API까지 키 타입을 구분해 잘못된 값 전달을 컴파일 시점에 막아야 할 때
- 도메인 규칙이 증가해 JPA Entity와 순수 도메인 모델의 분리 비용을 정당화할 때
- 영속성 기술을 교체해야 할 때
