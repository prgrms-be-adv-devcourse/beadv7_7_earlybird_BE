package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import feign.codec.EncodeException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.net.ssl.SSLHandshakeException;

@Component
public class OrderRemoteCallExecutor {
    public enum PaymentFailureOutcome {
        DEFINITIVE_FAILURE,
        AMBIGUOUS
    }

    private final Map<String, Retry> retries = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public <T> T execute(String operation, Supplier<T> call) {
        Supplier<T> circuitBroken = CircuitBreaker.decorateSupplier(circuitBreaker(operation), call);
        return Retry.decorateSupplier(retry(operation), circuitBroken).get();
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
                return feignException.status() < 0 || feignException.status() == 408
                        || feignException.status() == 429 || feignException.status() >= 500;
            }
            if (current instanceof BusinessException businessException) {
                return businessException.getStatus().is5xxServerError();
            }
        }
        return false;
    }

    public boolean isRetryable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof CallNotPermittedException) {
                return false;
            }
        }
        return isTechnical(failure);
    }

    public PaymentFailureOutcome classifyPaymentFailure(Throwable failure) {
        boolean definitelyNotDelivered = false;
        boolean ambiguous = false;
        boolean unclassifiedTransportFailure = false;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DecodeException) {
                ambiguous = true;
            }
            if (current instanceof FeignException feignException) {
                if (isServiceDiscoveryFailure(feignException)) {
                    definitelyNotDelivered = true;
                } else if (feignException.status() == 408 || feignException.status() >= 500) {
                    ambiguous = true;
                }
                if (feignException.status() < 0) {
                    unclassifiedTransportFailure = true;
                }
            }
            if (current instanceof BusinessException businessException
                    && businessException.getStatus().is5xxServerError()) {
                ambiguous = true;
            }
            if (current instanceof CallNotPermittedException
                    || current instanceof EncodeException
                    || current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof UnresolvedAddressException
                    || current instanceof HttpConnectTimeoutException
                    || current instanceof SSLHandshakeException) {
                definitelyNotDelivered = true;
            } else if (current instanceof SocketTimeoutException socketTimeoutException) {
                if (isConnectTimeout(socketTimeoutException)) {
                    definitelyNotDelivered = true;
                } else {
                    ambiguous = true;
                }
            } else if (current instanceof SocketException) {
                ambiguous = true;
            }
            if (current instanceof RetryableException || current instanceof IOException) {
                unclassifiedTransportFailure = true;
            }
        }
        if (ambiguous) {
            return PaymentFailureOutcome.AMBIGUOUS;
        }
        if (definitelyNotDelivered) {
            return PaymentFailureOutcome.DEFINITIVE_FAILURE;
        }
        return unclassifiedTransportFailure
                ? PaymentFailureOutcome.AMBIGUOUS
                : PaymentFailureOutcome.DEFINITIVE_FAILURE;
    }

    private boolean isServiceDiscoveryFailure(FeignException failure) {
        return failure.status() == HttpStatus.SERVICE_UNAVAILABLE.value()
                && failure.contentUTF8().startsWith("Load balancer does not contain an instance for the service ");
    }

    private boolean isConnectTimeout(SocketTimeoutException failure) {
        String message = failure.getMessage();
        if (message == null) {
            return false;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return normalizedMessage.contains("connect timed out") || normalizedMessage.contains("connect timeout");
    }

    private Retry retry(String operation) {
        return retries.computeIfAbsent(operation, key -> Retry.of(key, RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(100L, 2.0, 1_000L))
                .retryOnException(this::isRetryable)
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
