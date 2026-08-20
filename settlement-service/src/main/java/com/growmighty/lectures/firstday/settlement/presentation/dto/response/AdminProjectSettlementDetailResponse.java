// TODO(settlement-plan): Map new payout or review state without exposing provider payloads or persistence entities.
package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.query.AdminProjectSettlementDetail;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record AdminProjectSettlementDetailResponse(
        Long settlementId,
        Long creatorId,
        ProjectResponse project,
        OffsetDateTime confirmedAt,
        BreakdownResponse breakdown,
        PayoutResponse payout
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static AdminProjectSettlementDetailResponse from(AdminProjectSettlementDetail detail) {
        List<PayoutAttemptResponse> attempts = detail.attempts().stream()
                .map(PayoutAttemptResponse::from)
                .toList();
        return new AdminProjectSettlementDetailResponse(
                detail.settlementId(),
                detail.creatorId(),
                new ProjectResponse(detail.projectId()),
                toOffsetDateTime(detail.confirmedAt()),
                BreakdownResponse.from(detail),
                new PayoutResponse(
                        detail.settlementId(),
                        detail.status(),
                        detail.scheduledDate(),
                        toOffsetDateTime(detail.completedAt()),
                        new DestinationResponse(
                                detail.tossSellerId()
                        ),
                        attempts
                )
        );
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
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

        private static BreakdownResponse from(AdminProjectSettlementDetail detail) {
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
            Long settlementId,
            PayoutStatus status,
            LocalDate scheduledDate,
            OffsetDateTime completedAt,
            DestinationResponse destination,
            List<PayoutAttemptResponse> attempts
    ) {
    }

    public record DestinationResponse(String tossSellerId) {
    }

    public record PayoutAttemptResponse(
            Long attemptId,
            int sequence,
            String refPayoutId,
            String idempotencyKey,
            String tossPayoutId,
            BigDecimal amount,
            PayoutAttemptStatus status,
            String errorCode,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt
    ) {

        private static PayoutAttemptResponse from(PayoutAttempt attempt) {
            return new PayoutAttemptResponse(
                    attempt.id(),
                    attempt.sequence(),
                    attempt.refPayoutId(),
                    attempt.idempotencyKey(),
                    attempt.tossPayoutId(),
                    attempt.amount().amount(),
                    attempt.status(),
                    attempt.errorCode(),
                    toOffsetDateTime(attempt.requestedAt()),
                    toOffsetDateTime(attempt.completedAt())
            );
        }
    }
}
