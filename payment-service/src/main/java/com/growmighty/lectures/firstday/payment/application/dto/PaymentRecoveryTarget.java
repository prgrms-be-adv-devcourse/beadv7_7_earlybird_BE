package com.growmighty.lectures.firstday.payment.application.dto;

public record PaymentRecoveryTarget(
    Long paymentId,
    String paymentKey
) {
}
