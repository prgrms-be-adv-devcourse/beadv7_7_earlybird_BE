package com.growmighty.lectures.firstday.board.review.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingTest {

    @Test
    @DisplayName("1~5 사이 소수점 첫째자리 값으로 생성할 수 있다")
    void from_success() {
        Rating rating = Rating.from(new BigDecimal("4.5"));

        assertThat(rating.getValue()).isEqualByComparingTo("4.5");
    }

    @Test
    @DisplayName("null 값으로 생성하면 예외가 발생한다")
    void from_null_throws() {
        assertThatThrownBy(() -> Rating.from(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("최솟값(1) 미만이면 예외가 발생한다")
    void from_belowMin_throws() {
        assertThatThrownBy(() -> Rating.from(new BigDecimal("0.9")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최댓값(5) 초과면 예외가 발생한다")
    void from_aboveMax_throws() {
        assertThatThrownBy(() -> Rating.from(new BigDecimal("5.1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최솟값과 최댓값은 그대로 생성할 수 있다")
    void from_boundaryValues_success() {
        assertThat(Rating.from(BigDecimal.ONE).getValue()).isEqualByComparingTo("1");
        assertThat(Rating.from(BigDecimal.valueOf(5)).getValue()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("소수점 둘째자리 이상이면 예외가 발생한다")
    void from_tooManyDecimalPlaces_throws() {
        assertThatThrownBy(() -> Rating.from(new BigDecimal("4.53")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("트레일링 0으로 인해 scale만 커진 값은 소수점 첫째자리로 취급해 통과한다")
    void from_trailingZero_success() {
        Rating rating = Rating.from(new BigDecimal("4.50"));

        assertThat(rating.getValue()).isEqualByComparingTo("4.5");
    }

    @Nested
    @DisplayName("값 객체 동등성")
    class Equality {

        @Test
        @DisplayName("scale이 달라도 값이 같으면 동등하고 hashCode도 같다")
        void equals_ignoresScale() {
            Rating a = Rating.from(new BigDecimal("4.5"));
            Rating b = Rating.from(new BigDecimal("4.50"));

            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("값이 다르면 동등하지 않다")
        void notEquals_whenDifferent() {
            assertThat(Rating.from(new BigDecimal("4.5")))
                    .isNotEqualTo(Rating.from(new BigDecimal("3.5")));
        }
    }
}