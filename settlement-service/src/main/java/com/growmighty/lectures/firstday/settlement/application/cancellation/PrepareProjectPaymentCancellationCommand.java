package com.growmighty.lectures.firstday.settlement.application.cancellation;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import java.util.Objects;

public record PrepareProjectPaymentCancellationCommand(
        Long projectId,
        Long orderId,
        ProjectCancellationReason reason,
        String idempotencyKey
) {

    public PrepareProjectPaymentCancellationCommand {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
        }
        Objects.requireNonNull(reason, "결제 취소 사유는 필수입니다.");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등키는 필수입니다.");
        }
    }
}
