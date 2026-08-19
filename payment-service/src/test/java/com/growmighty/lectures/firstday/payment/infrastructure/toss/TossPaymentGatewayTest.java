package com.growmighty.lectures.firstday.payment.infrastructure.toss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayException;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayFailureType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TossPaymentGatewayTest {

    private final RestClient tossRestClient = mock(RestClient.class);
    private final RateLimiter paymentApprovalRateLimiter = RateLimiter.of(
        "paymentApprovalRateLimiter",
        RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build()
    );
    private final RateLimiter paymentLookupRateLimiter = RateLimiter.of(
        "paymentLookupRateLimiter",
        RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ZERO)
            .build()
    );
    private final TossPaymentGateway tossPaymentGateway = new TossPaymentGateway(
        tossRestClient,
        new ObjectMapper(),
        Retry.of("paymentApprovalRetry", RetryConfig.custom().maxAttempts(1).build()),
        CircuitBreaker.ofDefaults("paymentApprovalCircuitBreaker"),
        Retry.of("paymentLookupRetry", RetryConfig.custom().maxAttempts(1).build()),
        CircuitBreaker.ofDefaults("paymentLookupCircuitBreaker"),
        paymentApprovalRateLimiter,
        paymentLookupRateLimiter
    );

    // 변경 : 승인 RateLimiter permit 소진 시 승인 HTTP 호출 없이 재시도 대상으로 전환한다.
    @Test
    void approve_throwsUncertainExceptionWithoutTossCallWhenRateLimitExceeded() {
        paymentApprovalRateLimiter.acquirePermission(); // <-- 승인 API 전용 RateLimiter 소진

        assertThatThrownBy(() -> tossPaymentGateway.approve(
            "payment-key", "order-id", BigDecimal.valueOf(10_000), "idempotency-key"
        ))
            .isInstanceOf(PaymentGatewayException.class)
            .extracting(exception -> ((PaymentGatewayException) exception).getFailureType())
            .isEqualTo(PaymentGatewayFailureType.UNCERTAIN);

        verifyNoInteractions(tossRestClient);
    }

    // 변경 : 조회 RateLimiter permit 소진 시 조회 HTTP 호출 없이 재시도 대상으로 전환한다.
    @Test
    void getPayment_throwsUncertainExceptionWithoutTossCallWhenRateLimitExceeded() {
        paymentLookupRateLimiter.acquirePermission(); // <-- 조회 API 전용 RateLimiter 소진

        assertThatThrownBy(() -> tossPaymentGateway.getPayment("payment-key"))
            .isInstanceOf(PaymentGatewayException.class)
            .extracting(exception -> ((PaymentGatewayException) exception).getFailureType())
            .isEqualTo(PaymentGatewayFailureType.UNCERTAIN);

        verifyNoInteractions(tossRestClient);
    }
}
