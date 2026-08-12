package com.growmighty.lectures.firstday.payment.presentation.dto;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;

import java.math.BigDecimal;

public record PaymentPrepareResponse(
    Long paymentId,
    String pgOrderId,
    BigDecimal amount,
    String status
) {
    public static PaymentPrepareResponse from(PaymentPreparationInfo info) {
        return new PaymentPrepareResponse(
            info.paymentId(),
            info.pgOrderId(),
            info.amount(),
            info.status().getCode()
        );
    }
}
