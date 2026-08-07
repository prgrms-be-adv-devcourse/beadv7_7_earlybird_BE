// TODO(settlement-plan): Accept only reconciled successful-project payments and reject unreconciled or empty input.
package com.growmighty.lectures.firstday.settlement.application.settlement;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ConfirmProjectSettlementCommand(
        Long projectId,
        Long creatorId,
        List<Money> orderPaymentAmounts,
        LocalDate scheduledDate,
        LocalDateTime confirmedAt
) {

    public ConfirmProjectSettlementCommand {
        orderPaymentAmounts = List.copyOf(orderPaymentAmounts);
    }
}
