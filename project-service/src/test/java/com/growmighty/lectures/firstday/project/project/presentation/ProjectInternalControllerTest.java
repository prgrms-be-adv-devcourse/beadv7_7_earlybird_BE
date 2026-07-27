package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.FundedAmountUpdateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProjectInternalControllerTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectInternalController controller = new ProjectInternalController(projectService);

    @Test
    void updateFundedAmount_delegatesToService() {
        controller.updateFundedAmount(1L, new FundedAmountUpdateRequest(BigDecimal.valueOf(500_000)));

        verify(projectService).updateFundedAmount(1L, BigDecimal.valueOf(500_000));
    }
}
