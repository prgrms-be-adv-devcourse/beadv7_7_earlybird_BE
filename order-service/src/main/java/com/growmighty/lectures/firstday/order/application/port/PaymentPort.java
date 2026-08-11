package com.growmighty.lectures.firstday.order.application.port;

import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;

import java.math.BigDecimal;

public interface PaymentPort {

    PaymentResult pay(Long orderId, Long userId, BigDecimal amount);

    CancellationResult cancel(Long paymentId, BigDecimal amount);

    PaymentResult getPaymentResult(Long orderId);

    record CancellationResult(
            PaymentResult.Status status,
            BigDecimal amount,
            Long paymentId,
            Long orderId
    ) { }
}
