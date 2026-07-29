package com.growmighty.lectures.firstday.order.application.port;

import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;

import java.math.BigDecimal;

public interface PaymentPort {

    PaymentResult pay(Long orderId, Long userId, BigDecimal amount);

    RefundResult refund(Long orderId, BigDecimal amount);

    PaymentResult getPaymentResult(Long orderId);

    record RefundResult(
            PaymentResult.Status status,
            BigDecimal amount,
            String refundReference
    ) {
        public static RefundResult success(BigDecimal amount, String refundReference) {
            return new RefundResult(PaymentResult.Status.SUCCESS, amount, refundReference);
        }
    }
}
