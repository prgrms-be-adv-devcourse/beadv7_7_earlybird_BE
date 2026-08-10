package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementTest {

    @Test
    @DisplayName("주문 결제금액으로 확정 요율과 프로젝트 정산 금액을 계산한다")
    void calculatesConfirmedRatesAndAmounts() {
        ProjectSettlement settlement = confirm(List.of(
                Money.wons(10_015),
                Money.wons(20_240)
        ));

        assertThat(settlement)
                .extracting(
                        ProjectSettlement::paymentAndSettlementAgencyFeeRate,
                        ProjectSettlement::platformFeeRate,
                        ProjectSettlement::vatRate,
                        ProjectSettlement::baseAmount,
                        ProjectSettlement::paymentAndSettlementAgencyFeeAmount,
                        ProjectSettlement::paymentAndSettlementAgencyFeeVatAmount,
                        ProjectSettlement::platformFeeAmount,
                        ProjectSettlement::platformFeeVatAmount,
                        ProjectSettlement::otherDeductionAmount,
                        ProjectSettlement::creatorPayoutAmount
                )
                .containsExactly(
                        new BigDecimal("0.04"),
                        new BigDecimal("0.04"),
                        new BigDecimal("0.1"),
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
    @DisplayName("정산 확정 시점의 지급 대상과 최초 지급 상태를 고정한다")
    void fixesPayoutDestinationAndInitialStatus() {
        ProjectSettlement settlement = confirm(List.of(Money.wons(100_000)));

        assertThat(settlement.tossSellerId()).isEqualTo("seller-10");
        assertThat(settlement.bankCode()).isEqualTo("088");
        assertThat(settlement.maskedAccountNumber()).isEqualTo("********1234");
        assertThat(settlement.scheduledDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(settlement.status()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("프로젝트 정산 기준 금액이 0원이면 확정을 거부한다")
    void rejectsZeroBaseAmount() {
        assertThatThrownBy(() -> confirm(List.of(Money.wons(0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로젝트 정산 기준 금액은 0원보다 커야 합니다.");
    }

    private static ProjectSettlement confirm(List<Money> amounts) {
        return ProjectSettlement.confirm(
                1L,
                10L,
                amounts,
                CreatorPayoutProfile.registered(
                        10L,
                        "seller-10",
                        CreatorPayoutStatus.PAYOUT_READY,
                        "088",
                        "********1234",
                        LocalDateTime.of(2026, 7, 22, 9, 0)
                ),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );
    }
}
