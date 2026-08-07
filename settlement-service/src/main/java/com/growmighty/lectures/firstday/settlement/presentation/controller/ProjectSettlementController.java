// TODO(settlement-plan): Route manual requests to the same idempotent monthly-run interface used by the scheduler.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import com.growmighty.lectures.firstday.settlement.application.run.ProjectSettlementRunService;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.ProjectSettlementRunResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.request.RunProjectSettlementsRequest;
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

    private final ProjectSettlementRunService projectSettlementRunService;

    @PostMapping("/runs")
    public ProjectSettlementRunResponse run(@Valid @RequestBody RunProjectSettlementsRequest request) {
        return ProjectSettlementRunResponse.from(
                projectSettlementRunService.run(request.settlementMonth())
        );
    }
}
