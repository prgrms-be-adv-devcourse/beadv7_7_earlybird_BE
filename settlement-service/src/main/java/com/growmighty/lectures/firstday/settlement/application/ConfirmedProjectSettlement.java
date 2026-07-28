package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import java.time.LocalDate;

public record ConfirmedProjectSettlement(
        Long projectId,
        Long creatorId,
        Long settlementId,
        Long payoutObligationId,
        Money creatorPayoutAmount,
        PayoutObligationStatus payoutObligationStatus,
        LocalDate scheduledDate
) {
}
