package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.domain.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.SettlementFeePolicySnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminProjectSettlementDetail(
        Long settlementId,
        Long projectId,
        Long creatorId,
        LocalDateTime confirmedAt,
        SettlementFeePolicySnapshot feePolicySnapshot,
        SettlementBreakdown breakdown,
        Long payoutObligationId,
        PayoutObligationStatus status,
        LocalDate scheduledDate,
        LocalDateTime completedAt,
        String tossSellerId,
        String bankCode,
        String maskedAccountNumber,
        List<PayoutAttempt> attempts
) {

    public AdminProjectSettlementDetail {
        attempts = List.copyOf(attempts);
    }
}
