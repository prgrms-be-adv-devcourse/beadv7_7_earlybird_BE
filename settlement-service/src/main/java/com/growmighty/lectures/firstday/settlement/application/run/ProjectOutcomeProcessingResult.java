package com.growmighty.lectures.firstday.settlement.application.run;

import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import java.util.Objects;

public record ProjectOutcomeProcessingResult(
        Long projectId,
        ProjectOutcomeStatus outcomeStatus,
        ProjectOutcomeProcessingStatus processingStatus
) {

    public ProjectOutcomeProcessingResult {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        Objects.requireNonNull(outcomeStatus, "프로젝트 결과 상태는 필수입니다.");
        Objects.requireNonNull(processingStatus, "프로젝트 결과 처리 상태는 필수입니다.");
        if (!supports(outcomeStatus, processingStatus)) {
            throw new IllegalArgumentException("프로젝트 결과와 처리 상태가 일치하지 않습니다.");
        }
    }

    public static ProjectOutcomeProcessingResult settlementConfirmed(Long projectId) {
        return new ProjectOutcomeProcessingResult(
                projectId,
                ProjectOutcomeStatus.SUCCEEDED,
                ProjectOutcomeProcessingStatus.SETTLEMENT_CONFIRMED
        );
    }

    private static boolean supports(
            ProjectOutcomeStatus outcomeStatus,
            ProjectOutcomeProcessingStatus processingStatus
    ) {
        return switch (processingStatus) {
            case SETTLEMENT_CONFIRMED, SETTLEMENT_ALREADY_CONFIRMED ->
                    outcomeStatus == ProjectOutcomeStatus.SUCCEEDED;
            case PAYMENT_CANCELLATION_COMPLETED,
                    PAYMENT_CANCELLATION_PROCESSING,
                    PAYMENT_CANCELLATION_RETRYABLE_FAILED,
                    PAYMENT_CANCELLATION_FINAL_FAILED,
                    PAYMENT_CANCELLATION_UNKNOWN -> outcomeStatus != ProjectOutcomeStatus.SUCCEEDED;
            case OUTCOME_CONFLICT -> true;
        };
    }
}
