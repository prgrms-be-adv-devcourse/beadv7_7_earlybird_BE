package com.growmighty.lectures.firstday.payment.application.dto;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;

import java.math.BigDecimal;

public record PaymentPreparationInfo(
    Long paymentId,
    String pgOrderId,
    BigDecimal amount,
    PaymentStatus status
) {
    public static PaymentPreparationInfo from(Payment payment) {
        return new PaymentPreparationInfo(
            payment.getPaymentId(),
            payment.getPgOrderId(),
            payment.getAmount(),
            payment.getStatus()
        );
    }
}
