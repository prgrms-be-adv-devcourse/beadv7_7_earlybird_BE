package com.growmighty.lectures.firstday.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("음수 금액으로 생성하면 예외가 발생한다")
    void from_negative_throws() {
        assertThatThrownBy(() -> Money.from(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 금액으로 생성하면 예외가 발생한다")
    void from_null_throws() {
        assertThatThrownBy(() -> Money.from(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("더하기/빼기/곱하기/퍼센트 계산이 올바르다")
    void arithmetic() {
        Money ten = Money.from(BigDecimal.valueOf(10_000));

        assertThat(ten.plus(Money.from(BigDecimal.valueOf(5_000))).getValue())
                .isEqualByComparingTo("15000");
        assertThat(ten.minus(Money.from(BigDecimal.valueOf(4_000))).getValue())
                .isEqualByComparingTo("6000");
        assertThat(ten.times(3).getValue()).isEqualByComparingTo("30000");
        assertThat(ten.percentage(10).getValue()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("빼기 결과가 음수가 되면 예외가 발생한다")
    void minus_belowZero_throws() {
        Money small = Money.from(BigDecimal.valueOf(1_000));
        assertThatThrownBy(() -> small.minus(Money.from(BigDecimal.valueOf(2_000))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("크기 비교가 올바르다")
    void comparison() {
        Money fifty = Money.from(BigDecimal.valueOf(50_000));
        assertThat(fifty.isGreaterThanOrEqual(Money.from(BigDecimal.valueOf(50_000)))).isTrue();
        assertThat(fifty.isGreaterThanOrEqual(Money.from(BigDecimal.valueOf(49_999)))).isTrue();
        assertThat(fifty.isGreaterThanOrEqual(Money.from(BigDecimal.valueOf(50_001)))).isFalse();
    }

    @Nested
    @DisplayName("값 객체 동등성")
    class Equality {

        @Test
        @DisplayName("scale이 달라도 금액이 같으면 동등하고 hashCode도 같다")
        void equals_ignoresScale() {
            Money a = Money.from(new BigDecimal("50000"));
            Money b = Money.from(new BigDecimal("50000.00"));

            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("금액이 다르면 동등하지 않다")
        void notEquals_whenDifferent() {
            assertThat(Money.from(BigDecimal.valueOf(100)))
                    .isNotEqualTo(Money.from(BigDecimal.valueOf(200)));
        }
    }
}
