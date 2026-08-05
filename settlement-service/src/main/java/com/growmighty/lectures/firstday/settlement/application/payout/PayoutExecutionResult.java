package com.growmighty.lectures.firstday.settlement.application.payout;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import java.util.Objects;

public record PayoutExecutionResult(
        Long payoutObligationId,
        int attemptSequence,
        PayoutAttemptStatus attemptStatus,
        PayoutObligationStatus payoutObligationStatus
) {

    public PayoutExecutionResult {
        if (payoutObligationId == null || payoutObligationId <= 0) {
            throw new IllegalArgumentException("지급 의무 식별자는 양수여야 합니다.");
        }
        if (attemptSequence <= 0) {
            throw new IllegalArgumentException("지급 시도 순번은 양수여야 합니다.");
        }
        attemptStatus = Objects.requireNonNull(attemptStatus, "지급 시도 상태는 필수입니다.");
        payoutObligationStatus = Objects.requireNonNull(
                payoutObligationStatus,
                "지급 의무 상태는 필수입니다."
        );
    }
}
