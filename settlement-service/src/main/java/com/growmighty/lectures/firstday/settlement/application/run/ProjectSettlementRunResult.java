package com.growmighty.lectures.firstday.settlement.application.run;

import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import java.time.YearMonth;
import java.util.List;

public record ProjectSettlementRunResult(
        YearMonth settlementMonth,
        List<ProjectOutcomeProcessingResult> projectResults,
        List<ConfirmedProjectSettlement> confirmedSettlements
) {

    public ProjectSettlementRunResult {
        projectResults = List.copyOf(projectResults);
        confirmedSettlements = List.copyOf(confirmedSettlements);
    }

    public ProjectSettlementRunResult(
            YearMonth settlementMonth,
            List<ConfirmedProjectSettlement> confirmedSettlements
    ) {
        this(
                settlementMonth,
                confirmedSettlements.stream()
                        .map(ConfirmedProjectSettlement::projectId)
                        .map(ProjectOutcomeProcessingResult::settlementConfirmed)
                        .toList(),
                confirmedSettlements
        );
    }
}
