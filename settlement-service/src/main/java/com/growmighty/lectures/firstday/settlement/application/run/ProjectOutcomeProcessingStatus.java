// TODO(settlement-plan): Add REVIEW_REQUIRED semantics and remove direct cancellation statuses no longer owned here.
package com.growmighty.lectures.firstday.settlement.application.run;

public enum ProjectOutcomeProcessingStatus {

    SETTLEMENT_CONFIRMED,
    SETTLEMENT_ALREADY_CONFIRMED,
    REFUND_REQUEST_PENDING,
    OUTCOME_CONFLICT
}
