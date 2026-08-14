package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementTest {

    @Test
    @DisplayName("주문 결제금액으로 확정 요율과 프로젝트 정산 금액을 계산한다")
    void calculatesConfirmedRatesAndAmounts() {
        ProjectSettlement settlement = ProjectSettlement.confirm(
                1L, 10L, List.of(Money.wons(10_015), Money.wons(20_240)), LocalDateTime.of(2026, 7, 22, 10, 0));

        assertThat(settlement)
                .extracting(ProjectSettlement::paymentAndSettlementAgencyFeeRate, ProjectSettlement::platformFeeRate,
                        ProjectSettlement::vatRate, ProjectSettlement::baseAmount, ProjectSettlement::creatorPayoutAmount)
                .containsExactly(new BigDecimal("0.04"), new BigDecimal("0.04"), new BigDecimal("0.1"),
                        Money.wons(30_255), Money.wons(27_595));
    }

    @Test
    @DisplayName("프로젝트 정산 기준 금액이 0원이면 확정을 거부한다")
    void rejectsZeroBaseAmount() {
        assertThatThrownBy(() -> ProjectSettlement.confirm(
                1L, 10L, List.of(Money.wons(0)), LocalDateTime.of(2026, 7, 22, 10, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로젝트 정산 기준 금액은 0원보다 커야 합니다.");
    }
}
