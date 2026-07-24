package com.growmighty.lectures.firstday.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
public class Money {

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    protected Money() {
    }

    private Money(BigDecimal amount) {
        Objects.requireNonNull(amount, "금액은 null일 수 없습니다.");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0원 이상이어야 합니다: " + amount);
        }
        this.amount = amount.setScale(0);
    }

    public static Money wons(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money minus(Money other) {
        Objects.requireNonNull(other, "차감할 금액은 null일 수 없습니다.");
        return new Money(amount.subtract(other.amount));
    }

    public BigDecimal amount() {
        return amount;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Money other)) {
            return false;
        }
        return amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }
}
