package com.growmighty.lectures.firstday.order.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {
    private BigDecimal value;

    private Money(BigDecimal value) {
        this.value = value;
    }

    public static Money from(BigDecimal value) {
        Objects.requireNonNull(value, "금액은 null일 수 없습니다.");

        if (value.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("금액은 0원 이상이어야 합니다. 입력값: " + value);

        return new Money(value);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        return new Money(this.value.add(other.value));
    }

    public Money minus(Money other) {
        return from(this.value.subtract(other.value));
    }

    public Money times(int quantity) {
        return new Money(this.value.multiply(BigDecimal.valueOf(quantity)));
    }

    public Money percentage(int percent) {
        BigDecimal amount = this.value
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return new Money(amount);
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.value.compareTo(other.value) >= 0;
    }

    public boolean isSameAmount(Money other) {
        return this.value.compareTo(other.value) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return this.value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }
}
