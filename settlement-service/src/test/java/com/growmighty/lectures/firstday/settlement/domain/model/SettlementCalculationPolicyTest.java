package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementCalculationPolicyTest {

    @Test
    @DisplayName("Order의 주문 결제금액으로 프로젝트 정산 금액을 계산한다")
    void calculatesProjectSettlementAmounts() {
        SettlementCalculationPolicy policy = SettlementCalculationPolicy.current();

        SettlementBreakdown breakdown = policy.calculate(List.of(
                Money.wons(10_015),
                Money.wons(20_240)
        ));

        assertThat(policy.feePolicySnapshot()).isEqualTo(SettlementFeePolicySnapshot.of(
                new BigDecimal("0.04"),
                new BigDecimal("0.04"),
                new BigDecimal("0.10")
        ));
        assertThat(breakdown)
                .extracting(
                        SettlementBreakdown::baseAmount,
                        SettlementBreakdown::paymentAndSettlementAgencyFeeAmount,
                        SettlementBreakdown::paymentAndSettlementAgencyFeeVatAmount,
                        SettlementBreakdown::platformFeeAmount,
                        SettlementBreakdown::platformFeeVatAmount,
                        SettlementBreakdown::otherDeductionAmount,
                        SettlementBreakdown::creatorPayoutAmount
                )
                .containsExactly(
                        Money.wons(30_255),
                        Money.wons(1_209),
                        Money.wons(120),
                        Money.wons(1_210),
                        Money.wons(121),
                        Money.wons(0),
                        Money.wons(27_595)
                );
    }

    @Test
    @DisplayName("프로젝트 정산 기준 금액이 0원이면 계산을 거부한다")
    void rejectsZeroBaseAmount() {
        SettlementCalculationPolicy policy = SettlementCalculationPolicy.current();

        assertThatThrownBy(() -> policy.calculate(List.of(Money.wons(0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로젝트 정산 기준 금액은 0원보다 커야 합니다.");
    }
}
