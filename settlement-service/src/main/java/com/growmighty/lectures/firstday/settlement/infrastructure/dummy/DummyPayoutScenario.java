// TODO(settlement-plan): Mirror meaningful payout outcome classes without copying every provider status.
package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

public enum DummyPayoutScenario {
    COMPLETED,
    REQUESTED,
    IN_PROGRESS,
    RETRYABLE_FAILED,
    NON_RETRYABLE_FAILED,
    UNKNOWN
}
