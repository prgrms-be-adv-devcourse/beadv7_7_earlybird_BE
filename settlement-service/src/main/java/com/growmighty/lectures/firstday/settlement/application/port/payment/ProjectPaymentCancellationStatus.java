// TODO(settlement-plan): Delete this Settlement-owned refund status after Payment becomes the sole owner of refund results.
package com.growmighty.lectures.firstday.settlement.application.port.payment;

public enum ProjectPaymentCancellationStatus {
    COMPLETED,
    ALREADY_COMPLETED,
    NO_REFUND_REQUIRED,
    PROCESSING,
    RETRYABLE_FAILED,
    FINAL_FAILED,
    UNKNOWN
}
