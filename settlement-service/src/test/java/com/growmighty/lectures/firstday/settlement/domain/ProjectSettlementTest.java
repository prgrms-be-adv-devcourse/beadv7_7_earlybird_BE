package com.growmighty.lectures.firstday.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementTest {

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
                "2026-07",
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
                "2026-07",
                breakdown,
                destination,
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );

        assertThat(settlement.destinationSnapshot()).isEqualTo(destination);
    }
}
