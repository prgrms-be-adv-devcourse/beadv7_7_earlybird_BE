package com.growmighty.lectures.firstday.settlement.presentation.controller;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.settlement.application.run.PgReconciliationRunService;
import com.growmighty.lectures.firstday.settlement.application.run.ProjectPayoutRunService;
import com.growmighty.lectures.firstday.settlement.presentation.dto.request.RunPgReconciliationRequest;
import com.growmighty.lectures.firstday.settlement.presentation.dto.request.RunProjectPayoutRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class ProjectSettlementController {

    private final PgReconciliationRunService pgReconciliationRunService;
    private final ProjectPayoutRunService projectPayoutRunService;

    @PostMapping("/pg-reconciliations/runs")
    public ApiResponse<Void> runPgReconciliation(
            @Valid @RequestBody RunPgReconciliationRequest request
    ) {
        pgReconciliationRunService.run(request.settlementMonth());
        return ApiResponse.ok(null);
    }

    @PostMapping("/project-payouts/runs")
    public ApiResponse<Void> runProjectPayout(@Valid @RequestBody RunProjectPayoutRequest request) {
        projectPayoutRunService.run(request.payoutMonth());
        return ApiResponse.ok(null);
    }
}
