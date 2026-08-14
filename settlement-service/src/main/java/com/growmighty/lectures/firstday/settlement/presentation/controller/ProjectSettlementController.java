// TODO(settlement-plan): Route manual requests to the same idempotent monthly-run interface used by the scheduler.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import com.growmighty.lectures.firstday.settlement.application.run.PgReconciliationRunService;
import com.growmighty.lectures.firstday.settlement.application.run.ProjectPayoutRunService;
import com.growmighty.lectures.firstday.settlement.presentation.dto.request.RunPgReconciliationRequest;
import com.growmighty.lectures.firstday.settlement.presentation.dto.request.RunProjectPayoutRequest;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.PgReconciliationRunResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.ProjectPayoutRunResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/settlements")
@RequiredArgsConstructor
public class ProjectSettlementController {

    private final PgReconciliationRunService pgReconciliationRunService;
    private final ProjectPayoutRunService projectPayoutRunService;

    @PostMapping("/pg-reconciliations/runs")
    public PgReconciliationRunResponse runPgReconciliation(
            @Valid @RequestBody RunPgReconciliationRequest request
    ) {
        return PgReconciliationRunResponse.from(
                pgReconciliationRunService.run(request.settlementMonth())
        );
    }

    @PostMapping("/project-payouts/runs")
    public ProjectPayoutRunResponse runProjectPayout(@Valid @RequestBody RunProjectPayoutRequest request) {
        return ProjectPayoutRunResponse.from(projectPayoutRunService.run(request.payoutMonth()));
    }
}
