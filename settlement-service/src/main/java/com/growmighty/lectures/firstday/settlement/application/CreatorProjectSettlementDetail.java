package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.SettlementFeePolicySnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreatorProjectSettlementDetail(
        Long settlementId,
        Long projectId,
        String projectTitle,
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
