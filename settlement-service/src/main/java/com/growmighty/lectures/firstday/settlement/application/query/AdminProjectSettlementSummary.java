package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminProjectSettlementSummary(
        Long settlementId,
        Long projectId,
        Long creatorId,
        Money settlementBaseAmount,
        Money creatorPayoutAmount,
        PayoutObligationStatus status,
        LocalDateTime confirmedAt,
        LocalDate scheduledDate,
        LocalDateTime completedAt
) {
}
