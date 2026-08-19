package com.growmighty.lectures.firstday.refund.infrastructure.toss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayException;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayFailureType;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TossRefundGatewayTest {

    private final RestClient tossRestClient = mock(RestClient.class);
    private final RateLimiter paymentRefundRateLimiter = RateLimiter.of(
        "paymentRefundRateLimiter",
        RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build()
    );
    private final TossRefundGateway tossRefundGateway = new TossRefundGateway(
        tossRestClient,
        new ObjectMapper(),
        Retry.of("paymentRefundRetry", RetryConfig.custom().maxAttempts(1).build()),
        CircuitBreaker.ofDefaults("paymentRefundCircuitBreaker"),
        paymentRefundRateLimiter
    );

    // 추가 : RateLimiter permit 소진 시 Toss HTTP 호출 없이 재시도 대상으로 전환한다.
    @Test
    void refund_throwsUncertainExceptionWithoutTossCallWhenRateLimitExceeded() {
        paymentRefundRateLimiter.acquirePermission(); // <-- 환불 취소 API 전용 RateLimiter 소진

        assertThatThrownBy(() -> tossRefundGateway.refund("payment-key", RefundReason.USER_CANCEL, "idempotency-key"))
            .isInstanceOf(RefundGatewayException.class)
            .extracting(exception -> ((RefundGatewayException) exception).getFailureType())
            .isEqualTo(RefundGatewayFailureType.UNCERTAIN);

        verifyNoInteractions(tossRestClient);
    }
}
