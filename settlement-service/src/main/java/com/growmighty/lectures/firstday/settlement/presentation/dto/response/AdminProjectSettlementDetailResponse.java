package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.query.AdminProjectSettlementDetail;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementFeePolicySnapshot;
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
                BreakdownResponse.from(detail.feePolicySnapshot(), detail.breakdown()),
                new PayoutResponse(
                        detail.payoutObligationId(),
                        detail.status(),
                        detail.scheduledDate(),
                        toOffsetDateTime(detail.completedAt()),
                        new DestinationResponse(
                                detail.tossSellerId(),
                                detail.bankCode(),
                                detail.maskedAccountNumber()
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

        private static BreakdownResponse from(
                SettlementFeePolicySnapshot feePolicy,
                SettlementBreakdown breakdown
        ) {
            return new BreakdownResponse(
                    breakdown.baseAmount().amount(),
                    new FeeResponse(
                            feePolicy.paymentAndSettlementAgencyFeeRate(),
                            breakdown.paymentAndSettlementAgencyFeeAmount().amount(),
                            feePolicy.vatRate(),
                            breakdown.paymentAndSettlementAgencyFeeVatAmount().amount()
                    ),
                    new FeeResponse(
                            feePolicy.platformFeeRate(),
                            breakdown.platformFeeAmount().amount(),
                            feePolicy.vatRate(),
                            breakdown.platformFeeVatAmount().amount()
                    ),
                    breakdown.otherDeductionAmount().amount(),
                    breakdown.creatorPayoutAmount().amount()
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
            Long payoutObligationId,
            PayoutObligationStatus status,
            LocalDate scheduledDate,
            OffsetDateTime completedAt,
            DestinationResponse destination,
            List<PayoutAttemptResponse> attempts
    ) {
    }

    public record DestinationResponse(
            String tossSellerId,
            String bankCode,
            String maskedAccountNumber
    ) {
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
