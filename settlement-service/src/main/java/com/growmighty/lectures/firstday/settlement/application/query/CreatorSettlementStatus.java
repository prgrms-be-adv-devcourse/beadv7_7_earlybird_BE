package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;

public enum CreatorSettlementStatus {
    REGISTRATION_PENDING,
    SCHEDULED,
    PROCESSING,
    RETRY_WAITING,
    COMPLETED,
    ACTION_REQUIRED;

    public static CreatorSettlementStatus from(PayoutStatus payoutStatus) {
        return valueOf(payoutStatus.name());
    }
}
