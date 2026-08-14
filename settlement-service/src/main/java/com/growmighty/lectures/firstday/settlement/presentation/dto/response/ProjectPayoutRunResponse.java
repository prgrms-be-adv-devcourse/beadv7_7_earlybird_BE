package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.run.ProjectPayoutRunResult;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPayoutRun;
import java.time.YearMonth;

public record ProjectPayoutRunResponse(
        Long runId,
        YearMonth payoutMonth,
        ProjectPayoutRun.Status status
) {

    public static ProjectPayoutRunResponse from(ProjectPayoutRunResult result) {
        return new ProjectPayoutRunResponse(result.runId(), result.payoutMonth(), result.status());
    }
}
