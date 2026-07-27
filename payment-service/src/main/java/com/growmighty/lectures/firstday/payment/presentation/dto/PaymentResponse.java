package com.growmighty.lectures.firstday.payment.presentation.dto;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(
        Long paymentId,
        UUID orderId,
        BigDecimal amount,
        String status
) {
    public static PaymentResponse from(PaymentInfo info) {
        return new PaymentResponse(info.paymentId(), info.orderId(), info.amount(), info.status().name());
    }
}
