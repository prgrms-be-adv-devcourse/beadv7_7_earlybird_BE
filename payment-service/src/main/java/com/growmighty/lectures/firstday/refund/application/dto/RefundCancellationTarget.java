package com.growmighty.lectures.firstday.refund.application.dto;

import com.growmighty.lectures.firstday.refund.domain.RefundReason;

public record RefundCancellationTarget(
    Long refundId,
    String paymentKey,
    RefundReason reason
) {
}
