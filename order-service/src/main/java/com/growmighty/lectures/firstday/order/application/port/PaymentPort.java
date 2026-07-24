package com.growmighty.lectures.firstday.order.application.port;

import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentPort {

    PaymentResult pay(UUID orderId, Long userId, BigDecimal amount);

    RefundResult refund(UUID orderId, BigDecimal amount);

    PaymentResult getPaymentResult(UUID orderId);

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
