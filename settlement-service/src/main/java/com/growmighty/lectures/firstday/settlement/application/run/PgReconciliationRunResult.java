package com.growmighty.lectures.firstday.settlement.application.run;

import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import java.time.YearMonth;
import java.util.List;

public record PgReconciliationRunResult(
        Long runId,
        YearMonth settlementMonth,
        PgReconciliationRun.Status status,
        List<Long> confirmedOrderIds
) {

    public PgReconciliationRunResult {
        confirmedOrderIds = List.copyOf(confirmedOrderIds);
    }
}
