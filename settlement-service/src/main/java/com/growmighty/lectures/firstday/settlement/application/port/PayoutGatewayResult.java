package com.growmighty.lectures.firstday.settlement.application.port;

import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import java.util.Objects;

public sealed interface PayoutGatewayResult
        permits PayoutGatewayResult.Accepted, PayoutGatewayResult.Rejected {

    record Accepted(
            String payoutId,
            PayoutAttemptStatus status,
            String errorCode
    ) implements PayoutGatewayResult {

        public Accepted {
            if (payoutId == null || payoutId.isBlank()) {
                throw new IllegalArgumentException("토스 지급 식별자는 필수입니다.");
            }
            status = Objects.requireNonNull(status, "지급 시도 상태는 필수입니다.");
            if (status == PayoutAttemptStatus.UNKNOWN) {
                throw new IllegalArgumentException("결과 불명확 상태는 외부 응답으로 확정할 수 없습니다.");
            }
            if (status == PayoutAttemptStatus.FAILED
                    && (errorCode == null || errorCode.isBlank())) {
                throw new IllegalArgumentException("실패한 지급의 오류 코드는 필수입니다.");
            }
        }
    }

    record Rejected(String errorCode) implements PayoutGatewayResult {

        public Rejected {
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("거절된 지급 요청의 오류 코드는 필수입니다.");
            }
        }
    }
}
