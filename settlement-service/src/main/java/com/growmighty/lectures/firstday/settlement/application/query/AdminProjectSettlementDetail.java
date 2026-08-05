package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementFeePolicySnapshot;
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
