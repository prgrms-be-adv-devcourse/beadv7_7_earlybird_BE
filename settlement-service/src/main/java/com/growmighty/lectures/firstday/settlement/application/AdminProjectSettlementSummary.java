package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminProjectSettlementSummary(
        Long settlementId,
        Long projectId,
        String projectTitle,
        Long creatorId,
        Money settlementBaseAmount,
        Money creatorPayoutAmount,
        PayoutObligationStatus status,
        LocalDateTime confirmedAt,
        LocalDate scheduledDate,
        LocalDateTime completedAt
) {
}
