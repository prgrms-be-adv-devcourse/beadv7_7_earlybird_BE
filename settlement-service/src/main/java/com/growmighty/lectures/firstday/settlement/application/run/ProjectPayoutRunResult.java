package com.growmighty.lectures.firstday.settlement.application.run;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPayoutRun;
import java.time.YearMonth;

public record ProjectPayoutRunResult(
        Long runId,
        YearMonth payoutMonth,
        ProjectPayoutRun.Status status
) {
}
