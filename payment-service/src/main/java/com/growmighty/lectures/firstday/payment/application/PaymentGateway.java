package com.growmighty.lectures.firstday.payment.application;

import java.math.BigDecimal;

public interface PaymentGateway {
    PgApproval approve(
        String paymentKey,
        String pgOrderId,
        BigDecimal amount,
        String idempotencyKey
    );

    PgPayment getPayment(String paymentKey);

    void cancel(String paymentKey);

    record PgApproval(String paymentKey, String pgOrderId, BigDecimal amount) {
    }

    record PgPayment (String paymentKey, String pgOrderId, BigDecimal amount, PgPaymentStatus status) {

    }

    /**
     *  Toss DONE       → COMPLETED
     *   Toss ABORTED   → FAILED
     *   Toss EXPIRED   → EXPIRED
     *   Toss CANCELED  → CANCELLED
     *   그 외           → PENDING
     */

    enum PgPaymentStatus {
        COMPLETED,
        FAILED,
        EXPIRED,
        CANCELLED,
        PENDING
    }
}
