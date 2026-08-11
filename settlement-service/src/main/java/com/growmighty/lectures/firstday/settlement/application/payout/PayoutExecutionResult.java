// TODO(settlement-plan): Represent Toss-shaped payout outcomes without leaking dummy scenarios into the application interface.
package com.growmighty.lectures.firstday.settlement.application.payout;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import java.util.Objects;

public record PayoutExecutionResult(
        Long settlementId,
        int attemptSequence,
        PayoutAttemptStatus attemptStatus,
        PayoutStatus payoutStatus
) {

    public PayoutExecutionResult {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException("프로젝트 정산 식별자는 양수여야 합니다.");
        }
        if (attemptSequence <= 0) {
            throw new IllegalArgumentException("지급 시도 순번은 양수여야 합니다.");
        }
        attemptStatus = Objects.requireNonNull(attemptStatus, "지급 시도 상태는 필수입니다.");
        payoutStatus = Objects.requireNonNull(
                payoutStatus,
                "지급 상태는 필수입니다."
        );
    }
}
