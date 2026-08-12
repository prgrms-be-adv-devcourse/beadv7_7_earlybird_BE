// TODO(settlement-plan): Expose confirmed financial and payout state only; hide reconciliation evidence and event metadata.
package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.query.CreatorProjectSettlementDetail;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CreatorProjectSettlementDetailResponse(
        Long settlementId,
        ProjectResponse project,
        OffsetDateTime confirmedAt,
        BreakdownResponse breakdown,
        PayoutResponse payout
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static CreatorProjectSettlementDetailResponse from(CreatorProjectSettlementDetail detail) {
        return new CreatorProjectSettlementDetailResponse(
                detail.settlementId(),
                new ProjectResponse(detail.projectId()),
                toOffsetDateTime(detail.confirmedAt()),
                BreakdownResponse.from(detail),
                new PayoutResponse(
                        detail.status(),
                        detail.scheduledDate(),
                        toOffsetDateTime(detail.completedAt()),
                        new DestinationResponse(detail.bankCode(), detail.maskedAccountNumber())
                )
        );
    }

    private static OffsetDateTime toOffsetDateTime(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(SEOUL).toOffsetDateTime();
    }

    public record ProjectResponse(Long projectId) {
    }

    public record BreakdownResponse(
            BigDecimal settlementBaseAmount,
            FeeResponse paymentAndSettlementAgencyFee,
            FeeResponse platformFee,
            BigDecimal otherDeductionAmount,
            BigDecimal creatorPayoutAmount
    ) {

        private static BreakdownResponse from(CreatorProjectSettlementDetail detail) {
            return new BreakdownResponse(
                    detail.baseAmount().amount(),
                    new FeeResponse(
                            detail.paymentAndSettlementAgencyFeeRate(),
                            detail.paymentAndSettlementAgencyFeeAmount().amount(),
                            detail.vatRate(),
                            detail.paymentAndSettlementAgencyFeeVatAmount().amount()
                    ),
                    new FeeResponse(
                            detail.platformFeeRate(),
                            detail.platformFeeAmount().amount(),
                            detail.vatRate(),
                            detail.platformFeeVatAmount().amount()
                    ),
                    detail.otherDeductionAmount().amount(),
                    detail.creatorPayoutAmount().amount()
            );
        }
    }

    public record FeeResponse(
            BigDecimal rate,
            BigDecimal amount,
            BigDecimal vatRate,
            BigDecimal vatAmount
    ) {
    }

    public record PayoutResponse(
            PayoutStatus status,
            LocalDate scheduledDate,
            OffsetDateTime completedAt,
            DestinationResponse destination
    ) {
    }

    public record DestinationResponse(String bankCode, String maskedAccountNumber) {
    }
}
