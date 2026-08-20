package com.growmighty.lectures.firstday.order.application.port.dto;

import java.math.BigDecimal;

public record PaymentResult(
        Long paymentId,
        String pgOrderId,
        BigDecimal amount,
        Status status
) {
    public enum Status {
        SUCCESS,
        CANCELLED,
        FAILURE,
        PENDING,
        UNKNOWN
    }

    public static PaymentResult success(Long paymentId, BigDecimal amount) {
        return success(paymentId, null, amount);
    }

    public static PaymentResult success(Long paymentId, String pgOrderId, BigDecimal amount) {
        return new PaymentResult(paymentId, pgOrderId, amount, Status.SUCCESS);
    }

    public static PaymentResult failure(BigDecimal amount) {
        return failure(null, amount);
    }

    public static PaymentResult cancelled(Long paymentId, String pgOrderId, BigDecimal amount) {
        return new PaymentResult(paymentId, pgOrderId, amount, Status.CANCELLED);
    }

    public static PaymentResult failure(String pgOrderId, BigDecimal amount) {
        return new PaymentResult(null, pgOrderId, amount, Status.FAILURE);
    }

    public static PaymentResult unknown(BigDecimal amount) {
        return unknown(null, amount);
    }

    public static PaymentResult unknown(String pgOrderId, BigDecimal amount) {
        return new PaymentResult(null, pgOrderId, amount, Status.UNKNOWN);
    }

    public static PaymentResult pending(BigDecimal amount) {
        return pending(null, amount);
    }

    public static PaymentResult pending(String pgOrderId, BigDecimal amount) {
        return new PaymentResult(null, pgOrderId, amount, Status.PENDING);
    }
}
