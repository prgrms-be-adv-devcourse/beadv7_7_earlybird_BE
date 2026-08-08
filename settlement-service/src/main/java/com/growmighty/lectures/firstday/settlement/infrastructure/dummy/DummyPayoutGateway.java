// TODO(settlement-plan): Return deterministic Toss-shaped payout results keyed by refPayoutId and preserve idempotent retries.
package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.payout.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DummyPayoutGateway implements PayoutGateway {

    public static final String RETRYABLE_ERROR_CODE = "DUMMY_RETRYABLE_FAILURE";
    public static final String NON_RETRYABLE_ERROR_CODE = "DUMMY_ACTION_REQUIRED";

    private static final Logger log = LoggerFactory.getLogger(DummyPayoutGateway.class);
    private static final String PAYOUT_ID_PREFIX = "dummy-";

    private final DummyPayoutScenario scenario;

    public DummyPayoutGateway(DummyPayoutScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "더미 지급 시나리오는 필수입니다.");
        log.warn("더미 지급대행이 활성화되었습니다. 실제 송금은 수행되지 않습니다. scenario={}", scenario);
    }

    @Override
    public PayoutGatewayResult requestScheduledPayout(ScheduledPayoutRequest request) {
        Objects.requireNonNull(request, "예약 지급 요청은 필수입니다.");
        String payoutId = deterministicPayoutId(request.idempotencyKey());

        return switch (scenario) {
            case COMPLETED -> accepted(payoutId, PayoutAttemptStatus.COMPLETED);
            case REQUESTED -> accepted(payoutId, PayoutAttemptStatus.REQUESTED);
            case IN_PROGRESS -> accepted(payoutId, PayoutAttemptStatus.IN_PROGRESS);
            case RETRYABLE_FAILED -> new PayoutGatewayResult.Failed(
                    payoutId,
                    RETRYABLE_ERROR_CODE,
                    true
            );
            case NON_RETRYABLE_FAILED -> new PayoutGatewayResult.Failed(
                    payoutId,
                    NON_RETRYABLE_ERROR_CODE,
                    false
            );
            case UNKNOWN -> throw new PayoutGatewayException("더미 지급 결과가 불명확합니다.");
        };
    }

    private static PayoutGatewayResult.Accepted accepted(
            String payoutId,
            PayoutAttemptStatus status
    ) {
        return new PayoutGatewayResult.Accepted(payoutId, status);
    }

    private static String deterministicPayoutId(String idempotencyKey) {
        UUID value = UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        String compactValue = value.toString().replace("-", "").substring(0, 28);
        return PAYOUT_ID_PREFIX + compactValue;
    }
}
