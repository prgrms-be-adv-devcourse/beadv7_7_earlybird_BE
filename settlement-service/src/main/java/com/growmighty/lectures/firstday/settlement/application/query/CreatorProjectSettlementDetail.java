// TODO(settlement-plan): Keep creator detail independent of PG and Kafka metadata.
package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementFeePolicySnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreatorProjectSettlementDetail(
        Long settlementId,
        Long projectId,
        LocalDateTime confirmedAt,
        SettlementFeePolicySnapshot feePolicySnapshot,
        SettlementBreakdown breakdown,
        PayoutObligationStatus status,
        LocalDate scheduledDate,
        LocalDateTime completedAt,
        String bankCode,
        String maskedAccountNumber
) {
}
