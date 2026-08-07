package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class OrderRemoteCallExecutor {
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public <T> T execute(String operation, Supplier<T> call) {
        Supplier<T> retried = Retry.decorateSupplier(retry(operation), call);
        return CircuitBreaker.decorateSupplier(circuitBreaker(operation), retried).get();
    }

    public void execute(String operation, Runnable call) {
        execute(operation, () -> {
            call.run();
            return null;
        });
    }

    public boolean isTechnical(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RetryableException
                    || current instanceof CallNotPermittedException
                    || current instanceof IOException) {
                return true;
            }
            if (current instanceof FeignException feignException) {
                return feignException.status() < 0 || feignException.status() >= 500;
            }
            if (current instanceof BusinessException businessException) {
                return businessException.getStatus().is5xxServerError();
            }
        }
        return false;
    }

    private Retry retry(String operation) {
        return retries.computeIfAbsent(operation, key -> Retry.of(key, RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryOnException(this::isTechnical)
                .build()));
    }

    private CircuitBreaker circuitBreaker(String operation) {
        return circuitBreakers.computeIfAbsent(operation, key -> CircuitBreaker.of(key,
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(4)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .recordException(this::isTechnical)
                        .build()));
    }
}
