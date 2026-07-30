package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.domain.Money;
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
