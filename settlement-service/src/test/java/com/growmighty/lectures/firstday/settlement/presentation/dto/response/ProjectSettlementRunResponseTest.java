// TODO(settlement-plan): Replace legacy cancellation result mapping with monthly run status and review summary mapping.
package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import static com.growmighty.lectures.firstday.settlement.application.run.ProjectOutcomeProcessingStatus.OUTCOME_CONFLICT;
import static com.growmighty.lectures.firstday.settlement.application.run.ProjectOutcomeProcessingStatus.REFUND_REQUEST_PENDING;
import static com.growmighty.lectures.firstday.settlement.application.run.ProjectOutcomeProcessingStatus.SETTLEMENT_ALREADY_CONFIRMED;
import static com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus.CANCELLED;
import static com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus.FAILED;
import static com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.growmighty.lectures.firstday.settlement.application.run.ProjectOutcomeProcessingResult;
import com.growmighty.lectures.firstday.settlement.application.run.ProjectSettlementRunResult;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementRunResponseTest {

    @Test
    @DisplayName("프로젝트별 정산·환불 요청 대기 상태를 내부 실행 응답에 보존한다")
    void preservesProjectOutcomeProcessingStatuses() {
        ProjectSettlementRunResult result = new ProjectSettlementRunResult(
                YearMonth.of(2026, 7),
                List.of(
                        new ProjectOutcomeProcessingResult(101L, SUCCEEDED, SETTLEMENT_ALREADY_CONFIRMED),
                        new ProjectOutcomeProcessingResult(103L, FAILED, REFUND_REQUEST_PENDING),
                        new ProjectOutcomeProcessingResult(104L, CANCELLED, REFUND_REQUEST_PENDING),
                        new ProjectOutcomeProcessingResult(108L, CANCELLED, OUTCOME_CONFLICT)
                ),
                List.of()
        );

        ProjectSettlementRunResponse response = ProjectSettlementRunResponse.from(result);

        assertThat(response.projectResults())
                .extracting(
                        ProjectSettlementRunResponse.ProjectResultResponse::projectId,
                        ProjectSettlementRunResponse.ProjectResultResponse::outcomeStatus,
                        ProjectSettlementRunResponse.ProjectResultResponse::processingStatus
                )
                .containsExactly(
                        tuple(101L, SUCCEEDED, SETTLEMENT_ALREADY_CONFIRMED),
                        tuple(103L, FAILED, REFUND_REQUEST_PENDING),
                        tuple(104L, CANCELLED, REFUND_REQUEST_PENDING),
                        tuple(108L, CANCELLED, OUTCOME_CONFLICT)
                );
    }
}
