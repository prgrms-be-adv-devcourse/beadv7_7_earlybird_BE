package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DummyPayoutGatewayTest {

    @Test
    @DisplayName("기본 성공 시나리오는 실제 송금 없이 완료 결과를 반환한다")
    void completesPayout() {
        DummyPayoutGateway gateway = new DummyPayoutGateway(DummyPayoutScenario.COMPLETED);

        PayoutGatewayResult result = gateway.requestScheduledPayout(request("idempotency-1"));

        assertThat(result).isInstanceOfSatisfying(
                PayoutGatewayResult.Accepted.class,
                accepted -> {
                    assertThat(accepted.status()).isEqualTo(PayoutAttemptStatus.COMPLETED);
                    assertThat(accepted.payoutId())
                            .startsWith("dummy-")
                            .hasSizeLessThanOrEqualTo(35);
                }
        );
    }

    @Test
    @DisplayName("같은 멱등키는 항상 같은 더미 지급 식별자와 결과로 수렴한다")
    void convergesByIdempotencyKey() {
        DummyPayoutGateway gateway = new DummyPayoutGateway(DummyPayoutScenario.IN_PROGRESS);

        PayoutGatewayResult first = gateway.requestScheduledPayout(request("same-key"));
        PayoutGatewayResult repeated = gateway.requestScheduledPayout(request("same-key"));
        PayoutGatewayResult different = gateway.requestScheduledPayout(request("different-key"));

        assertThat(repeated).isEqualTo(first);
        assertThat(((PayoutGatewayResult.Accepted) different).payoutId())
                .isNotEqualTo(((PayoutGatewayResult.Accepted) first).payoutId());
    }

    @Test
    @DisplayName("접수와 처리 중 시나리오를 결정적으로 선택한다")
    void selectsPendingScenarios() {
        assertThat(acceptedStatus(DummyPayoutScenario.REQUESTED))
                .isEqualTo(PayoutAttemptStatus.REQUESTED);
        assertThat(acceptedStatus(DummyPayoutScenario.IN_PROGRESS))
                .isEqualTo(PayoutAttemptStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("재시도 가능 여부를 포함한 실패 시나리오를 선택한다")
    void selectsFailureScenarios() {
        PayoutGatewayResult.Failed retryable = failed(DummyPayoutScenario.RETRYABLE_FAILED);
        PayoutGatewayResult.Failed actionRequired = failed(
                DummyPayoutScenario.NON_RETRYABLE_FAILED
        );

        assertThat(retryable.retryable()).isTrue();
        assertThat(retryable.errorCode()).isEqualTo(DummyPayoutGateway.RETRYABLE_ERROR_CODE);
        assertThat(actionRequired.retryable()).isFalse();
        assertThat(actionRequired.errorCode())
                .isEqualTo(DummyPayoutGateway.NON_RETRYABLE_ERROR_CODE);
    }

    @Test
    @DisplayName("결과 불명확 시나리오는 지급 결과를 확정하지 않는다")
    void returnsUnknownAsException() {
        DummyPayoutGateway gateway = new DummyPayoutGateway(DummyPayoutScenario.UNKNOWN);

        assertThatThrownBy(() -> gateway.requestScheduledPayout(request("idempotency-1")))
                .isInstanceOf(PayoutGatewayException.class);
    }

    private static PayoutAttemptStatus acceptedStatus(DummyPayoutScenario scenario) {
        DummyPayoutGateway gateway = new DummyPayoutGateway(scenario);
        return ((PayoutGatewayResult.Accepted) gateway.requestScheduledPayout(
                request("idempotency-1")
        )).status();
    }

    private static PayoutGatewayResult.Failed failed(DummyPayoutScenario scenario) {
        DummyPayoutGateway gateway = new DummyPayoutGateway(scenario);
        return (PayoutGatewayResult.Failed) gateway.requestScheduledPayout(
                request("idempotency-1")
        );
    }

    private static ScheduledPayoutRequest request(String idempotencyKey) {
        return new ScheduledPayoutRequest(
                "earlybird-payout-1-1",
                "dummy-seller-1",
                LocalDate.of(2026, 8, 3),
                Money.wons(91_200),
                "얼리버드",
                idempotencyKey
        );
    }
}
