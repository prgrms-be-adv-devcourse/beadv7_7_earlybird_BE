package com.growmighty.lectures.firstday.settlement.presentation.dto;

import com.growmighty.lectures.firstday.settlement.application.AdminProjectSettlementSummary;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record AdminProjectSettlementListItemResponse(
        Long settlementId,
        Long projectId,
        Long creatorId,
        BigDecimal settlementBaseAmount,
        BigDecimal creatorPayoutAmount,
        PayoutObligationStatus status,
        OffsetDateTime confirmedAt,
        LocalDate scheduledDate,
        OffsetDateTime completedAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static AdminProjectSettlementListItemResponse from(AdminProjectSettlementSummary summary) {
        return new AdminProjectSettlementListItemResponse(
                summary.settlementId(),
                summary.projectId(),
                summary.creatorId(),
                summary.settlementBaseAmount().amount(),
                summary.creatorPayoutAmount().amount(),
                summary.status(),
                summary.confirmedAt().atZone(SEOUL).toOffsetDateTime(),
                summary.scheduledDate(),
                summary.completedAt() == null
                        ? null
                        : summary.completedAt().atZone(SEOUL).toOffsetDateTime()
        );
    }
}
