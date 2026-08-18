package com.growmighty.lectures.firstday.payment.config;

import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayException;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayFailureType;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayException;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayFailureType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PaymentApprovalResilienceConfig {

    @Bean
    public RateLimiter tossApiRateLimiter() {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofMillis(750))
            .timeoutDuration(Duration.ZERO)
            .build();

        return RateLimiter.of("tossApiRateLimiter", rateLimiterConfig);
    }

    @Bean
    public Retry paymentApprovalRetry() {
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryOnException(this::isUncertainFailure)
            .build();

        return Retry.of("paymentApprovalRetry", retryConfig);
    }

    @Bean
    public CircuitBreaker paymentApprovalCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordException(this::isUncertainFailure)
            .build();

        return CircuitBreaker.of("PaymentApprovalCircuitBreaker", circuitBreakerConfig);
    }

    @Bean
    public Retry paymentLookupRetry() {
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofSeconds(1))
            .retryOnException(this::isUncertainFailure)
            .build();

        return Retry.of("paymentLookupRetry", retryConfig);
    }

    @Bean
    public CircuitBreaker paymentLookupCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordException(this::isUncertainFailure)
            .build();

        return CircuitBreaker.of("PaymentLookupCircuitBreaker", circuitBreakerConfig);
    }

    @Bean
    public Retry paymentRefundRetry() {
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryOnException(this::isRefundUncertainFailure)
            .build();

        return Retry.of("paymentRefundRetry", retryConfig);
    }

    @Bean
    public CircuitBreaker paymentRefundCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordException(this::isRefundUncertainFailure)
            .build();

        return CircuitBreaker.of("paymentRefundCircuitBreaker", circuitBreakerConfig);
    }

    private boolean isRefundUncertainFailure(Throwable throwable) {
        return throwable instanceof RefundGatewayException exception && exception.getFailureType() == RefundGatewayFailureType.UNCERTAIN;
    }

    private boolean isUncertainFailure(Throwable throwable) {
        return throwable instanceof PaymentGatewayException exception && exception.getFailureType() == PaymentGatewayFailureType.UNCERTAIN;
    }
}
