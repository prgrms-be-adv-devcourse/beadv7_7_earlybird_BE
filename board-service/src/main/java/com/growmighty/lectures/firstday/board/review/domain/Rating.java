package com.growmighty.lectures.firstday.board.review.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rating {
    private static final BigDecimal MIN = BigDecimal.ONE;
    private static final BigDecimal MAX = BigDecimal.valueOf(5);

    private BigDecimal value;

    private Rating(BigDecimal value) {
        this.value = value;
    }

    public static Rating from(BigDecimal value) {
        Objects.requireNonNull(value, "평점은 null일 수 없습니다.");

        if (value.stripTrailingZeros().scale() > 1) {
            throw new IllegalArgumentException("평점은 소수점 첫째자리까지만 허용됩니다. 입력값: " + value);
        }
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("평점은 1~5 사이여야 합니다. 입력값: " + value);
        }

        return new Rating(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rating other)) {
            return false;
        }
        return this.value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }
}