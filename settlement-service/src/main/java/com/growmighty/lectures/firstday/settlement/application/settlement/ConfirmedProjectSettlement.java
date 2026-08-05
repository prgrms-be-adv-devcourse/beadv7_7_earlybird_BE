package com.growmighty.lectures.firstday.settlement.application.settlement;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import java.time.LocalDate;
import java.util.Objects;

public record ConfirmedProjectSettlement(
        Long projectId,
        Long creatorId,
        Long settlementId,
        Long payoutObligationId,
        Money creatorPayoutAmount,
        PayoutObligationStatus payoutObligationStatus,
        LocalDate scheduledDate
) {

    public ConfirmedProjectSettlement withPayoutObligationStatus(PayoutObligationStatus status) {
        return new ConfirmedProjectSettlement(
                projectId,
                creatorId,
                settlementId,
                payoutObligationId,
                creatorPayoutAmount,
                Objects.requireNonNull(status, "지급 의무 상태는 필수입니다."),
                scheduledDate
        );
    }
}
