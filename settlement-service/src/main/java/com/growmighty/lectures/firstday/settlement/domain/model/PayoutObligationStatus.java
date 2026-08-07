// TODO(settlement-plan): Align obligation transitions with preparation, in-flight, completion, and operator-action states.
package com.growmighty.lectures.firstday.settlement.domain.model;

public enum PayoutObligationStatus {
    CREATOR_PAYOUT_PROFILE_WAITING,
    SCHEDULED,
    PROCESSING,
    RETRY_WAITING,
    COMPLETED,
    ACTION_REQUIRED
}
