package com.growmighty.lectures.firstday.payment.application;

import java.math.BigDecimal;

public interface PaymentGateway {
    PgApproval approve(
        String paymentKey,
        String pgOrderId,
        BigDecimal amount,
        String idempotencyKey
    );

    void cancel(String paymentKey);

    record PgApproval(String paymentKey, String pgOrderId, BigDecimal amount) {
    }
}
