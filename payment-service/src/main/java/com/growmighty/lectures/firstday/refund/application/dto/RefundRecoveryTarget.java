package com.growmighty.lectures.firstday.refund.application.dto;


public record RefundRecoveryTarget(
    Long refundId,
    String paymentKey
) {
}
