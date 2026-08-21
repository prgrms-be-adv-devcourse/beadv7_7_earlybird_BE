# Payment-Service 기술 의사결정 요약 `#002`

- 날짜 : 2026-08-17

## 무슨 상황 인가요?

- 문제 상황: `Payment.paymentKey`, `Payment.approveIdempotencyKey`, `Refund.cancelIdempotencyKey`는 AES-256 암복호화가 필요한 값입니다.
- 기존에는 각 엔티티 필드에 `@Convert(converter = PaymentSensitiveDataConverter.class)`를 선언했습니다.
- 이 방식은 도메인 엔티티가 infrastructure의 Converter 구현체를 직접 참조하게 만듭니다.

```text
domain entity -> infrastructure PaymentSensitiveDataConverter
```

- `Payment`와 `Refund`는 JPA 매핑과 상태 전이 로직을 함께 가지는 엔티티 겸용 도메인 모델입니다. 이번 결정은 모델 전체를 분리하지 않고, 암복호화 구현체의 직접 의존성만 제거하는 것을 범위로 합니다.

## 무엇을 목표로 하나요?

- 목표 품질 속성 : 보안성, 유지 보수성
  - 암호화 대상이 아닌 일반 `String` 컬럼에는 암복호화가 적용되지 않아야 합니다.
  - 엔티티가 infrastructure Converter 구현체를 직접 import하지 않아야 합니다.
  - 저장·조회 시 암복호화가 자동으로 수행되어 유스케이스별 누락 가능성을 줄여야 합니다.
  - 기존 `VARCHAR` 스키마와 상태 전이 로직을 유지해 회귀 범위를 최소화해야 합니다.

## 무엇을 선택 했나요?

- `SensitiveValue`와 `AttributeConverter`의 `autoApply = true`를 사용합니다.
- 암호화 대상 필드는 `String` 대신 `SensitiveValue`로 선언합니다.
- infrastructure의 `SensitiveValueConverter`가 `AttributeConverter<SensitiveValue, String>`으로 저장 시 암호화, 조회 시 복호화를 담당합니다.
- 외부 PG 호출 시에만 application 계층에서 `SensitiveValue.value()`로 평문을 꺼냅니다.

```text
Payment / Refund entity
        |
        +-- SensitiveValue
                  |
                  +-- SensitiveValueConverter (autoApply = true)
                            |
                            +-- PaymentSensitiveDataCrypto (AES-256/GCM)
```

- 희생/비용:
  - `Payment`와 `Refund`는 여전히 JPA 엔티티 겸용 모델입니다.
  - `BaseEntity`와 JPA 어노테이션 의존성은 남습니다.
  - `SensitiveValue`는 민감값 여부만 표현하며 payment key와 idempotency key를 컴파일 시점에 구분하지는 않습니다.

## 선택의 근거는 어떻게 되나요?

- `autoApply` Converter는 `SensitiveValue` 타입 필드에만 적용되므로 일반 `String` 컬럼에 영향을 주지 않습니다.
- 엔티티에서 Converter 구현체 import와 `@Convert` 선언을 제거할 수 있습니다.
- JPA/Hibernate가 영속화와 조회 시점의 변환을 담당하므로 application에서 암복호화 호출을 반복할 필요가 없습니다.
- 기존 데이터베이스 컬럼이 `VARCHAR`이므로 스키마 변경 없이 적용할 수 있습니다.
- 현재 목표인 직접 의존성 제거에 비해 변경량과 회귀 위험이 작습니다.

## 선택지의 한계는 무엇인가요?

- 순수 도메인 모델을 달성한 것은 아닙니다. JPA 엔티티 겸용 모델을 유지하는 점진적 개선입니다.
- `SensitiveValue` 하나로는 payment key, 승인 멱등 키, 환불 멱등 키의 서로 다른 정책을 표현할 수 없습니다.
- 키별 검증·마스킹·보존 정책이 달라지거나 Port 경계에서도 타입 구분이 필요해지면 의미별 VO 분리가 필요합니다.

## 다른 선택지는 무엇이 있었나요?

- 기타 선택지 1 : 순수 도메인 모델과 JPA 엔티티를 분리합니다.
  - JPA와 Converter 의존성을 도메인에서 모두 제거할 수 있지만, 별도 Entity·Mapper·Repository Adapter가 필요하고 현재 범위 대비 변경량과 회귀 위험이 큽니다.
- 기타 선택지 2 : application Port를 통해 암복호화합니다.
  - 구현을 Port 뒤로 숨길 수 있지만, 유스케이스마다 암복호화 호출이 반복되고 평문·암호문이 모두 `String`이라 잘못된 값 전달 위험이 남습니다.
