package com.growmighty.lectures.firstday.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("금액에서 공제액을 차감한다")
    void subtractsDeductionFromAmount() {
        Money result = Money.wons(10_000).minus(Money.wons(1_100));

        assertThat(result).isEqualTo(Money.wons(8_900));
    }
}
