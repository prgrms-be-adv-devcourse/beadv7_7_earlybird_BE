// TODO(settlement-plan): Keep creator summary fields minimal and avoid duplicating calculation logic.
package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreatorProjectSettlementSummary(
        Long settlementId,
        Long projectId,
        Money settlementBaseAmount,
        Money creatorPayoutAmount,
        CreatorSettlementStatus status,
        LocalDateTime confirmedAt,
        LocalDate scheduledDate,
        LocalDateTime completedAt
) {
}
