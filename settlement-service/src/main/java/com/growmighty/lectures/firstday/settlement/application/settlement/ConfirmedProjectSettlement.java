// TODO(settlement-plan): Keep the confirmation result immutable and independent of Kafka and PG transport models.
package com.growmighty.lectures.firstday.settlement.application.settlement;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import java.time.LocalDate;
import java.util.Objects;

public record ConfirmedProjectSettlement(
        Long projectId,
        Long creatorId,
        Long settlementId,
        Money creatorPayoutAmount,
        PayoutStatus payoutStatus,
        LocalDate scheduledDate
) {

    public ConfirmedProjectSettlement withPayoutStatus(PayoutStatus status) {
        return new ConfirmedProjectSettlement(
                projectId,
                creatorId,
                settlementId,
                creatorPayoutAmount,
                Objects.requireNonNull(status, "지급 상태는 필수입니다."),
                scheduledDate
        );
    }
}
