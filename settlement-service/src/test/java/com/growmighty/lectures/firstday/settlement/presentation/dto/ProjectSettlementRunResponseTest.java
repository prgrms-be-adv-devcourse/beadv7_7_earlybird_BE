package com.growmighty.lectures.firstday.settlement.presentation.dto;

import static com.growmighty.lectures.firstday.settlement.application.ProjectOutcomeProcessingStatus.OUTCOME_CONFLICT;
import static com.growmighty.lectures.firstday.settlement.application.ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED;
import static com.growmighty.lectures.firstday.settlement.application.ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_FINAL_FAILED;
import static com.growmighty.lectures.firstday.settlement.application.ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_PROCESSING;
import static com.growmighty.lectures.firstday.settlement.application.ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_RETRYABLE_FAILED;
import static com.growmighty.lectures.firstday.settlement.application.ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_UNKNOWN;
import static com.growmighty.lectures.firstday.settlement.application.ProjectOutcomeProcessingStatus.SETTLEMENT_ALREADY_CONFIRMED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus.CANCELLED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus.FAILED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.growmighty.lectures.firstday.settlement.application.ProjectOutcomeProcessingResult;
import com.growmighty.lectures.firstday.settlement.application.ProjectSettlementRunResult;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementRunResponseTest {

    @Test
    @DisplayName("프로젝트별 정산·결제 취소 처리 상태를 내부 실행 응답에 보존한다")
    void preservesProjectOutcomeProcessingStatuses() {
        ProjectSettlementRunResult result = new ProjectSettlementRunResult(
                YearMonth.of(2026, 7),
                List.of(
                        new ProjectOutcomeProcessingResult(101L, SUCCEEDED, SETTLEMENT_ALREADY_CONFIRMED),
                        new ProjectOutcomeProcessingResult(103L, FAILED, PAYMENT_CANCELLATION_COMPLETED),
                        new ProjectOutcomeProcessingResult(104L, CANCELLED, PAYMENT_CANCELLATION_PROCESSING),
                        new ProjectOutcomeProcessingResult(105L, FAILED, PAYMENT_CANCELLATION_RETRYABLE_FAILED),
                        new ProjectOutcomeProcessingResult(106L, CANCELLED, PAYMENT_CANCELLATION_FINAL_FAILED),
                        new ProjectOutcomeProcessingResult(107L, FAILED, PAYMENT_CANCELLATION_UNKNOWN),
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
                        tuple(103L, FAILED, PAYMENT_CANCELLATION_COMPLETED),
                        tuple(104L, CANCELLED, PAYMENT_CANCELLATION_PROCESSING),
                        tuple(105L, FAILED, PAYMENT_CANCELLATION_RETRYABLE_FAILED),
                        tuple(106L, CANCELLED, PAYMENT_CANCELLATION_FINAL_FAILED),
                        tuple(107L, FAILED, PAYMENT_CANCELLATION_UNKNOWN),
                        tuple(108L, CANCELLED, OUTCOME_CONFLICT)
                );
    }
}
