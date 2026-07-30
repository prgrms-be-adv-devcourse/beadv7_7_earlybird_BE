package com.growmighty.lectures.firstday.settlement.application.port;

import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import java.util.Objects;

public sealed interface PayoutGatewayResult
        permits PayoutGatewayResult.Accepted, PayoutGatewayResult.Failed {

    record Accepted(
            String payoutId,
            PayoutAttemptStatus status
    ) implements PayoutGatewayResult {

        public Accepted {
            if (payoutId == null || payoutId.isBlank()) {
                throw new IllegalArgumentException("지급대행 식별자는 필수입니다.");
            }
            status = Objects.requireNonNull(status, "지급 시도 상태는 필수입니다.");
            if (status == PayoutAttemptStatus.FAILED || status == PayoutAttemptStatus.UNKNOWN) {
                throw new IllegalArgumentException("실패 또는 결과 불명확 상태는 확정 결과가 아닙니다.");
            }
        }
    }

    record Failed(
            String payoutId,
            String errorCode,
            boolean retryable
    ) implements PayoutGatewayResult {

        public Failed {
            if (payoutId != null && payoutId.isBlank()) {
                throw new IllegalArgumentException("지급대행 식별자는 비어 있을 수 없습니다.");
            }
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("실패한 지급의 오류 코드는 필수입니다.");
            }
        }
    }
}
