package com.growmighty.lectures.firstday.order.application.port.dto;

import java.math.BigDecimal;

public record PaymentResult(
        Long paymentId,
        BigDecimal amount,
        Status status
) {
    public enum Status {
        SUCCESS,
        FAILURE,
        PENDING,
        UNKNOWN
    }

    public static PaymentResult success(Long paymentId, BigDecimal amount) {
        return new PaymentResult(paymentId, amount, Status.SUCCESS);
    }

    public static PaymentResult failure(BigDecimal amount) {
        return new PaymentResult(null, amount, Status.FAILURE);
    }

    public static PaymentResult unknown(BigDecimal amount) {
        return new PaymentResult(null, amount, Status.UNKNOWN);
    }

    public static PaymentResult pending(BigDecimal amount) {
        return new PaymentResult(null, amount, Status.PENDING);
    }
}
