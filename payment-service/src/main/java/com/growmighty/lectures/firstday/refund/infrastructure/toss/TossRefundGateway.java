package com.growmighty.lectures.firstday.refund.infrastructure.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossErrorResponse;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossPaymentResponse;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayException;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayFailureType;
import com.growmighty.lectures.firstday.refund.application.port.RefundGateway;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.infrastructure.dto.TossCancelRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "payment.gateway",
    havingValue = "toss"
)
public class TossRefundGateway implements RefundGateway {
    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;
    private final Retry paymentRefundRetry;
    private final CircuitBreaker paymentRefundCircuitBreaker;
    private final RateLimiter paymentRefundRateLimiter;

    @Override
    public void refund(String paymentKey, RefundReason reason, String idempotencyKey) {
        Supplier<Void> refundSupplier = () -> {
            requestRefund(paymentKey, reason, idempotencyKey);
            return null;
        };

        Supplier<Void> rateLimitedSupplier = RateLimiter.decorateSupplier(
            paymentRefundRateLimiter,
            refundSupplier
        );

        Supplier<Void> retrySupplier = Retry.decorateSupplier(
            paymentRefundRetry,
            rateLimitedSupplier
        );

        Supplier<Void> circuitBreakerSupplier = CircuitBreaker.decorateSupplier(
            paymentRefundCircuitBreaker,
            retrySupplier
        );

        try {
            circuitBreakerSupplier.get();
        } catch (RequestNotPermitted exception) {
            throw new RefundGatewayException(
                HttpStatus.SERVICE_UNAVAILABLE,
                RefundGatewayFailureType.UNCERTAIN,
                "토스 환불 요청 한도를 초과했습니다. 잠시 후 재시도합니다."
            );
        } catch (CallNotPermittedException exception) {
            throw new RefundGatewayException(
                HttpStatus.SERVICE_UNAVAILABLE,
                RefundGatewayFailureType.UNCERTAIN,
                "토스 환불 요청이 일시적으로 차단되었습니다."
            );
        }
    }

    private void requestRefund(String paymentKey, RefundReason reason, String idempotencyKey) {
        try {
            TossPaymentResponse response = tossRestClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header("Idempotency-key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TossCancelRequest(reason.getCode()))
                .retrieve()
                .body(TossPaymentResponse.class);

            if (response == null || !"CANCELED".equals(response.status())) {
                throw new RefundGatewayException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    RefundGatewayFailureType.UNCERTAIN,
                    "토스 환불 결과를 확인할 수 없습니다."
                );
            }
        } catch (RestClientResponseException exception) {
            throw toRefundGatewayException(exception);
        } catch (ResourceAccessException exception) {
            throw new RefundGatewayException(
                HttpStatus.SERVICE_UNAVAILABLE,
                RefundGatewayFailureType.UNCERTAIN,
                "토스 결제 서버에 연결할 수 없습니다."
            );
        }
    }

    private RefundGatewayException toRefundGatewayException(RestClientResponseException exception) {
        try {
            TossErrorResponse errorResponse = objectMapper.readValue(
                exception.getResponseBodyAsString(),
                TossErrorResponse.class
            );

            HttpStatus status = exception.getStatusCode().is5xxServerError()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.CONFLICT;

            return new RefundGatewayException(
                status,
                resolveFailureType(exception, errorResponse),
                errorResponse.message()
            );
        } catch (JsonProcessingException ignored) {
            return new RefundGatewayException(
                HttpStatus.SERVICE_UNAVAILABLE,
                RefundGatewayFailureType.UNCERTAIN,
                "토스 환불 오류 응답을 해석할 수 없습니다."
            );
        }
    }

    private RefundGatewayFailureType resolveFailureType(
        RestClientResponseException exception,
        TossErrorResponse errorResponse
    ) {
        int statusCode = exception.getStatusCode().value();
        String tossCode = errorResponse.code();

        if (exception.getStatusCode().is5xxServerError()
            || statusCode == HttpStatus.REQUEST_TIMEOUT.value()
            || statusCode == HttpStatus.TOO_MANY_REQUESTS.value()
            || "PROVIDER_ERROR".equals(tossCode)
            || "ALREADY_CANCELED_PAYMENT".equals(tossCode)
            || "ALREADY_REFUND_PAYMENT".equals(tossCode)
            || "ALREADY_REFUNDING_PAYMENT".equals(tossCode)
            || "FORBIDDEN_CONSECUTIVE_REQUEST".equals(tossCode)) {
            return RefundGatewayFailureType.UNCERTAIN;
        }
        return RefundGatewayFailureType.DEFINITIVE;
    }
}
