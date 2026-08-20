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
        String refundRequestId,
        Instant publishedAt,
        Instant processedAt,
        Payout payout,
        Refund refund
) {

    public enum Type {
        PAYOUT,
        REFUND
    }

    public enum RefundPublishStatus {
        REQUESTED,
        PUBLISHED
    }

    public enum RefundProcessingStatus {
        AWAITING_RESULT,
        COMPLETED,
        ACTION_REQUIRED
    }

    public enum RefundStatus {
        REQUESTED,
        PROCESSING,
        COMPLETED,
        ACTION_REQUIRED
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
}
