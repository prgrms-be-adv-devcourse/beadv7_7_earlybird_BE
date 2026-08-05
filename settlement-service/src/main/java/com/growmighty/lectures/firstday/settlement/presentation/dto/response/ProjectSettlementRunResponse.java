package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.run.ProjectOutcomeProcessingResult;
import com.growmighty.lectures.firstday.settlement.application.run.ProjectOutcomeProcessingStatus;
import com.growmighty.lectures.firstday.settlement.application.run.ProjectSettlementRunResult;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record ProjectSettlementRunResponse(
        YearMonth settlementMonth,
        List<ProjectResultResponse> projectResults,
        List<ConfirmedSettlementResponse> confirmedSettlements
) {

    public static ProjectSettlementRunResponse from(ProjectSettlementRunResult result) {
        return new ProjectSettlementRunResponse(
                result.settlementMonth(),
                result.projectResults().stream()
                        .map(ProjectResultResponse::from)
                        .toList(),
                result.confirmedSettlements().stream()
                        .map(ConfirmedSettlementResponse::from)
                        .toList()
        );
    }

    public record ProjectResultResponse(
            Long projectId,
            ProjectOutcomeStatus outcomeStatus,
            ProjectOutcomeProcessingStatus processingStatus
    ) {

        private static ProjectResultResponse from(ProjectOutcomeProcessingResult result) {
            return new ProjectResultResponse(
                    result.projectId(),
                    result.outcomeStatus(),
                    result.processingStatus()
            );
        }
    }

    public record ConfirmedSettlementResponse(
            Long projectId,
            Long creatorId,
            Long settlementId,
            Long payoutObligationId,
            BigDecimal creatorPayoutAmount,
            PayoutObligationStatus payoutObligationStatus,
            LocalDate scheduledDate
    ) {

        private static ConfirmedSettlementResponse from(ConfirmedProjectSettlement settlement) {
            return new ConfirmedSettlementResponse(
                    settlement.projectId(),
                    settlement.creatorId(),
                    settlement.settlementId(),
                    settlement.payoutObligationId(),
                    settlement.creatorPayoutAmount().amount(),
                    settlement.payoutObligationStatus(),
                    settlement.scheduledDate()
            );
        }
    }
}
