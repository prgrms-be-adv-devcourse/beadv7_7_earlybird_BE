// TODO(settlement-plan): Keep domain lifecycle states small and translate Toss REQUESTED, COMPLETED, and FAILED at the gateway seam.
package com.growmighty.lectures.firstday.settlement.domain.model;

public enum PayoutAttemptStatus {
    REQUESTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELED,
    UNKNOWN
}
