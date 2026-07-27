package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ConfirmProjectSettlementCommand(
        Long projectId,
        String projectTitle,
        Long creatorId,
        List<Money> finalEffectivePaymentAmounts,
        LocalDate scheduledDate,
        LocalDateTime confirmedAt
) {

    public ConfirmProjectSettlementCommand {
        finalEffectivePaymentAmounts = List.copyOf(finalEffectivePaymentAmounts);
    }
}
