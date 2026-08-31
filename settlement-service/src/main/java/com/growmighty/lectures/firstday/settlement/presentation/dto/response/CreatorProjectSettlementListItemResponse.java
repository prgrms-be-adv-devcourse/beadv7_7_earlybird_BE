// TODO(settlement-plan): Keep creator list mapping minimal and stable across the Kafka migration.
package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.query.CreatorProjectSettlementSummary;
import com.growmighty.lectures.firstday.settlement.application.query.CreatorSettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CreatorProjectSettlementListItemResponse(
        Long settlementId,
        Long projectId,
        BigDecimal settlementBaseAmount,
        BigDecimal creatorPayoutAmount,
        CreatorSettlementStatus status,
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
