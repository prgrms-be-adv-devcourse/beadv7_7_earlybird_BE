package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;

public enum CreatorSettlementStatus {
    REGISTRATION_PENDING,
    PAYOUT_PENDING,
    APPROVAL_REQUIRED,
    KYC_REQUIRED,
    PAYOUT_UNAVAILABLE,
    RECONCILIATION_REVIEW_REQUIRED,
    SETTLEMENT_PENDING,
    REFUND_PENDING,
    REFUND_REQUESTED,
    REFUND_PROCESSING,
    REFUND_COMPLETED,
    REFUND_ACTION_REQUIRED,
    SCHEDULED,
    PROCESSING,
    RETRY_WAITING,
    COMPLETED,
    ACTION_REQUIRED;

    public static CreatorSettlementStatus from(PayoutStatus payoutStatus) {
        return valueOf(payoutStatus.name());
    }

    public static CreatorSettlementStatus from(CreatorPayoutStatus payoutStatus) {
        return switch (payoutStatus) {
            case PAYOUT_READY -> PAYOUT_PENDING;
            default -> valueOf(payoutStatus.name());
        };
    }
}
