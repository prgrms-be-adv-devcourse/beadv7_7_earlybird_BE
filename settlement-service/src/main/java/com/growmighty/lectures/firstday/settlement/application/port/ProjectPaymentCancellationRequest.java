package com.growmighty.lectures.firstday.settlement.application.port;

import java.util.Objects;

public record ProjectPaymentCancellationRequest(
        Long orderId,
        ProjectCancellationReason reason,
        String idempotencyKey
) {

    public ProjectPaymentCancellationRequest {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
        }
        Objects.requireNonNull(reason, "결제 취소 사유는 필수입니다.");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등키는 필수입니다.");
        }
    }
}
