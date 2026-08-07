// TODO(settlement-plan): Delete this synchronous refund result after Payment owns per-order outcomes asynchronously.
package com.growmighty.lectures.firstday.settlement.application.port.payment;

import java.util.Objects;

public record ProjectPaymentCancellationResult(
        Long orderId,
        ProjectPaymentCancellationStatus status
) {

    public ProjectPaymentCancellationResult {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
        }
        Objects.requireNonNull(status, "결제 취소 처리 상태는 필수입니다.");
    }
}
