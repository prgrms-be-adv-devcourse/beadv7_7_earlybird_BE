package com.growmighty.lectures.firstday.settlement.presentation.dto;

import com.growmighty.lectures.firstday.settlement.application.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.ProjectSettlementRunResult;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record ProjectSettlementRunResponse(
        YearMonth settlementMonth,
        List<ConfirmedSettlementResponse> confirmedSettlements
) {

    public static ProjectSettlementRunResponse from(ProjectSettlementRunResult result) {
        return new ProjectSettlementRunResponse(
                result.settlementMonth(),
                result.confirmedSettlements().stream()
                        .map(ConfirmedSettlementResponse::from)
                        .toList()
        );
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
