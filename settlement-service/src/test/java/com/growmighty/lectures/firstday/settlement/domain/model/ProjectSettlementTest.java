// TODO(settlement-plan): Verify immutable confirmation from reconciled positive inputs and one settlement per project.
package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementTest {

    @Test
    @DisplayName("정산 확정 시점의 수수료 정책을 원본으로 고정한다")
    void fixesProjectAndFeePolicySnapshotAtConfirmation() {
        SettlementFeePolicySnapshot feePolicySnapshot = SettlementFeePolicySnapshot.of(
                new BigDecimal("0.04"),
                new BigDecimal("0.04"),
                new BigDecimal("0.10")
        );
        SettlementBreakdown breakdown = SettlementBreakdown.of(
                Money.wons(100_000),
                Money.wons(4_000),
                Money.wons(400),
                Money.wons(4_000),
                Money.wons(400),
                Money.wons(0),
                Money.wons(91_200)
        );

        ProjectSettlement settlement = ProjectSettlement.confirm(
                1L,
                10L,
                feePolicySnapshot,
                breakdown,
                PayoutDestinationSnapshot.of(10L, "seller-10", "088", "********1234"),
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );

        assertThat(settlement.feePolicySnapshot()).isEqualTo(feePolicySnapshot);
    }

    @Test
    @DisplayName("프로젝트의 창작자 지급액을 확정한다")
    void confirmsCreatorPayoutAmount() {
        SettlementBreakdown breakdown = SettlementBreakdown.of(
                Money.wons(100_000),
                Money.wons(4_000),
                Money.wons(400),
                Money.wons(4_000),
                Money.wons(400),
                Money.wons(0),
                Money.wons(91_200)
        );

        ProjectSettlement settlement = ProjectSettlement.confirm(
                1L,
                10L,
                SettlementFeePolicySnapshot.current(),
                breakdown,
                PayoutDestinationSnapshot.of(10L, "seller-10", "088", "********1234"),
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );

        assertThat(settlement.creatorPayoutAmount()).isEqualTo(Money.wons(91_200));
    }

    @Test
    @DisplayName("정산 확정 시점의 창작자 지급 대상을 고정한다")
    void fixesPayoutDestinationAtConfirmation() {
        SettlementBreakdown breakdown = SettlementBreakdown.of(
                Money.wons(100_000),
                Money.wons(4_000),
                Money.wons(400),
                Money.wons(4_000),
                Money.wons(400),
                Money.wons(0),
                Money.wons(91_200)
        );
        PayoutDestinationSnapshot destination = PayoutDestinationSnapshot.of(
                10L,
                "seller-10",
                "088",
                "********1234"
        );

        ProjectSettlement settlement = ProjectSettlement.confirm(
                1L,
                10L,
                SettlementFeePolicySnapshot.current(),
                breakdown,
                destination,
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );

        assertThat(settlement.destinationSnapshot()).isEqualTo(destination);
    }
}
