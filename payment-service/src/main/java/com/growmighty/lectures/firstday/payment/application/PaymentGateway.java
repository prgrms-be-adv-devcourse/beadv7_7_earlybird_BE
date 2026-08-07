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

    record PgApproval(String paymentKey, String pgOrderId, BigDecimal amount) {
    }

    record PgPayment (String paymentKey, String pgOrderId, BigDecimal amount, PgPaymentStatus status) {

    }

    enum PgPaymentStatus {
        COMPLETED,
        FAILED,
        EXPIRED,
        CANCELLED,
        PENDING;

        /**
         * Toss 결제 상태를 Payment 내부 상태 표현으로 변환한다.
         * 알 수 없거나 비어 있는 상태는 결제 처리 중으로 간주한다.
         */
        public static PgPaymentStatus fromTossStatus(String status) {
            if (status == null) {
                return PENDING;
            }

            return switch (status) {
                case "DONE" -> COMPLETED;
                case "ABORTED" -> FAILED;
                case "EXPIRED" -> EXPIRED;
                case "CANCELED" -> CANCELLED;
                default -> PENDING;
            };
        }
    }
}
