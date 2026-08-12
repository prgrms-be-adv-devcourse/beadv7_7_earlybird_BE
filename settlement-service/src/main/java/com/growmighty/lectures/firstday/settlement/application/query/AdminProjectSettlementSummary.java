// TODO(settlement-plan): Keep summary fields minimal and derive status in the query module.
package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminProjectSettlementSummary(
        Long settlementId,
        Long projectId,
        Long creatorId,
        Money settlementBaseAmount,
        Money creatorPayoutAmount,
        PayoutStatus status,
        LocalDateTime confirmedAt,
        LocalDate scheduledDate,
        LocalDateTime completedAt
) {
}
