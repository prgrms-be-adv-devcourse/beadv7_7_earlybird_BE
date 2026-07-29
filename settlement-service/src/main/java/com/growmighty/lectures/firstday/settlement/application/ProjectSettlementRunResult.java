package com.growmighty.lectures.firstday.settlement.application;

import java.time.YearMonth;
import java.util.List;

public record ProjectSettlementRunResult(
        YearMonth settlementMonth,
        List<ConfirmedProjectSettlement> confirmedSettlements
) {

    public ProjectSettlementRunResult {
        confirmedSettlements = List.copyOf(confirmedSettlements);
    }
}
