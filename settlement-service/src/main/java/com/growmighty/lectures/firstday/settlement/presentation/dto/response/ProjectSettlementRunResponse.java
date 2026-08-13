// TODO(settlement-plan): Return settlementMonth, run status, and review summary instead of legacy direct-cancellation outcomes.
package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.run.ProjectSettlementRunResult;
import java.time.YearMonth;
import java.util.List;

public record ProjectSettlementRunResponse(
        YearMonth settlementMonth,
        List<Long> confirmedOrderIds
) {

    public static ProjectSettlementRunResponse from(ProjectSettlementRunResult result) {
        return new ProjectSettlementRunResponse(
                result.settlementMonth(),
                result.confirmedOrderIds()
        );
    }
}
