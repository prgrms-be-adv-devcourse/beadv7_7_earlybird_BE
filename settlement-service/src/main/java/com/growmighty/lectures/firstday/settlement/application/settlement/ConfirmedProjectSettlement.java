// TODO(settlement-plan): Keep the confirmation result immutable and independent of Kafka and PG transport models.
package com.growmighty.lectures.firstday.settlement.application.settlement;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import java.time.LocalDate;
import java.util.Optional;

public record ConfirmedProjectSettlement(
        Long projectId,
        Long creatorId,
        Long settlementId,
        Money creatorPayoutAmount,
        Optional<PayoutStatus> payoutStatus,
        Optional<LocalDate> scheduledDate
) {

    public boolean hasPayoutObligation() {
        return payoutStatus.isPresent();
    }
}
