// TODO(settlement-plan): Add REVIEW_REQUIRED semantics and remove direct cancellation statuses no longer owned here.
package com.growmighty.lectures.firstday.settlement.application.run;

public enum ProjectOutcomeProcessingStatus {

    SETTLEMENT_CONFIRMED,
    SETTLEMENT_ALREADY_CONFIRMED,
    PAYMENT_CANCELLATION_COMPLETED,
    PAYMENT_CANCELLATION_PROCESSING,
    PAYMENT_CANCELLATION_RETRYABLE_FAILED,
    PAYMENT_CANCELLATION_FINAL_FAILED,
    PAYMENT_CANCELLATION_UNKNOWN,
    OUTCOME_CONFLICT
}
