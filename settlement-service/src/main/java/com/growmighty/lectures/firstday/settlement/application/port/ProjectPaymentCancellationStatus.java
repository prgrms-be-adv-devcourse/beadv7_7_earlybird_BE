package com.growmighty.lectures.firstday.settlement.application.port;

public enum ProjectPaymentCancellationStatus {
    COMPLETED,
    ALREADY_COMPLETED,
    NO_REFUND_REQUIRED,
    PROCESSING,
    RETRYABLE_FAILED,
    FINAL_FAILED,
    UNKNOWN
}
