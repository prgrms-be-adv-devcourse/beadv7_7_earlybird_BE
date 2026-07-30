package com.growmighty.lectures.firstday.settlement.domain;

public enum ProjectPaymentCancellationCommandStatus {
    REQUESTED,
    COMPLETED,
    ALREADY_COMPLETED,
    NO_REFUND_REQUIRED,
    PROCESSING,
    RETRYABLE_FAILED,
    FINAL_FAILED,
    UNKNOWN;

    public boolean shouldRequestResult() {
        return switch (this) {
            case REQUESTED, PROCESSING, RETRYABLE_FAILED, UNKNOWN -> true;
            case COMPLETED, ALREADY_COMPLETED, NO_REFUND_REQUIRED, FINAL_FAILED -> false;
        };
    }
}
