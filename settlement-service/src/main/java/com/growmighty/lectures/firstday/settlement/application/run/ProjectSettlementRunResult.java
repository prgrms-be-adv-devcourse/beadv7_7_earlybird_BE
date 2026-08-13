// TODO(settlement-plan): Expose the monthly run status and reconciliation summary without leaking internal projections.
package com.growmighty.lectures.firstday.settlement.application.run;

import java.time.YearMonth;
import java.util.List;

public record ProjectSettlementRunResult(
        YearMonth settlementMonth,
        List<Long> confirmedOrderIds
) {

    public ProjectSettlementRunResult {
        confirmedOrderIds = List.copyOf(confirmedOrderIds);
    }
}
