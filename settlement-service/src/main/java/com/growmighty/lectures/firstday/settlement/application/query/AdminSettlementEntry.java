package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminSettlementEntry(
        Type type,
        Long projectId,
        String projectName,
        Long refundRequestId,
        Instant publishedAt,
        Instant processedAt,
        Payout payout,
        Refund refund,
        RegistrationPending registrationPending,
        PendingPayout pendingPayout
) {

    public enum Type {
        PAYOUT,
        REFUND,
        REGISTRATION_PENDING,
        PAYOUT_PENDING,
        APPROVAL_REQUIRED,
        KYC_REQUIRED,
        PAYOUT_UNAVAILABLE,
        RECONCILIATION_REVIEW_REQUIRED,
        SETTLEMENT_PENDING,
        REFUND_PENDING
    }

    public enum RefundStatus {
        REQUESTED,
        PROCESSING,
        COMPLETED,
        ACTION_REQUIRED;

        public static RefundStatus of(boolean published, String paymentResultStatus) {
            if (!published) return REQUESTED;
            if (paymentResultStatus == null) return PROCESSING;
            return "COMPLETED".equals(paymentResultStatus) ? COMPLETED : ACTION_REQUIRED;
        }
    }

    public record Payout(
            Long settlementId,
            Long creatorId,
            Money settlementBaseAmount,
            Money creatorPayoutAmount,
            PayoutStatus status,
            LocalDateTime confirmedAt,
            LocalDate scheduledDate
    ) {
    }

    public record Refund(
            ProjectCancellationReason reason,
            Instant requestedAt,
            RefundStatus refundStatus,
            Instant paymentResultAt,
            int paymentCount
    ) {
    }

    public record RegistrationPending(
            Long settlementId,
            Long creatorId,
            Money settlementBaseAmount,
            Money creatorPayoutAmount,
            LocalDateTime confirmedAt
    ) {
    }

    public record PendingPayout(
            Long settlementId,
            Long creatorId,
            Money settlementBaseAmount,
            Money creatorPayoutAmount,
            LocalDateTime confirmedAt
    ) {
    }
}
