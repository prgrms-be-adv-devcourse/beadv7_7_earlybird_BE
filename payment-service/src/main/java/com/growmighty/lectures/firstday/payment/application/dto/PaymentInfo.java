package com.growmighty.lectures.firstday.payment.application.dto;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentInfo(
        Long paymentId,
        UUID orderId,
        BigDecimal amount,
        PaymentStatus status
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(payment.getPaymentId(), payment.getOrderId(), payment.getAmount(), payment.getStatus());
    }
}
