package com.growmighty.lectures.firstday.payment.application.dto;


import java.math.BigDecimal;

public record PaymentRecoveryTarget(
    Long paymentId,
    String paymentKey,
    String pgOrderId,
    BigDecimal amount
) {
}
