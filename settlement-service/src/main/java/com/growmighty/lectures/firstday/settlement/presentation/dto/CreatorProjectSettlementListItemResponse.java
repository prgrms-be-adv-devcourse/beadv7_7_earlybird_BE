package com.growmighty.lectures.firstday.settlement.presentation.dto;

import com.growmighty.lectures.firstday.settlement.application.CreatorProjectSettlementSummary;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CreatorProjectSettlementListItemResponse(
        Long settlementId,
        Long projectId,
        BigDecimal settlementBaseAmount,
        BigDecimal creatorPayoutAmount,
        PayoutObligationStatus status,
        OffsetDateTime confirmedAt,
        LocalDate scheduledDate,
        OffsetDateTime completedAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static CreatorProjectSettlementListItemResponse from(CreatorProjectSettlementSummary summary) {
        return new CreatorProjectSettlementListItemResponse(
                summary.settlementId(),
                summary.projectId(),
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
