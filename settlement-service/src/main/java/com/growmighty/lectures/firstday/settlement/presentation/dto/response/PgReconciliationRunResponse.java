package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.run.PgReconciliationRunResult;
import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import java.time.YearMonth;
import java.util.List;

public record PgReconciliationRunResponse(
        Long runId,
        YearMonth settlementMonth,
        PgReconciliationRun.Status status,
        List<Long> confirmedOrderIds
) {

    public static PgReconciliationRunResponse from(PgReconciliationRunResult result) {
        return new PgReconciliationRunResponse(
                result.runId(),
                result.settlementMonth(),
                result.status(),
                result.confirmedOrderIds()
        );
    }
}
