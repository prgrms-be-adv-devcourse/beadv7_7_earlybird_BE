package com.growmighty.lectures.firstday.settlement.presentation;

import com.growmighty.lectures.firstday.settlement.application.ProjectSettlementRunService;
import com.growmighty.lectures.firstday.settlement.presentation.dto.ProjectSettlementRunResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.RunProjectSettlementsRequest;
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
