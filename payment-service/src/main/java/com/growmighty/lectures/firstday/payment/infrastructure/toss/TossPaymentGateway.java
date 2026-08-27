package com.growmighty.lectures.firstday.payment.infrastructure.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayException;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayFailureType;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossConfirmRequest;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossErrorResponse;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossPaymentResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(
    name = "payment.gateway",
    havingValue = "toss",
    matchIfMissing = true
)
public class TossPaymentGateway implements PaymentGateway {
    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;

    private final Retry paymentApprovalRetry;
    private final CircuitBreaker paymentApprovalCircuitBreaker;

    private final Retry paymentLookupRetry;
    private final CircuitBreaker paymentLookupCircuitBreaker;

    private final RateLimiter paymentApprovalRateLimiter;
    private final RateLimiter paymentLookupRateLimiter;


    public TossPaymentGateway(
        RestClient tossRestClient,
        ObjectMapper objectMapper,
        @Qualifier("paymentApprovalRetry") Retry paymentApprovalRetry,
        @Qualifier("paymentApprovalCircuitBreaker") CircuitBreaker paymentApprovalCircuitBreaker,
        @Qualifier("paymentLookupRetry") Retry paymentLookupRetry,
        @Qualifier("paymentLookupCircuitBreaker") CircuitBreaker paymentLookupCircuitBreaker,
        @Qualifier("paymentApprovalRateLimiter") RateLimiter paymentApprovalRateLimiter,
        @Qualifier("paymentLookupRateLimiter") RateLimiter paymentLookupRateLimiter
    ) {
        this.tossRestClient = tossRestClient;
        this.objectMapper = objectMapper;
        this.paymentApprovalRetry = paymentApprovalRetry;
        this.paymentApprovalCircuitBreaker = paymentApprovalCircuitBreaker;
        this.paymentLookupRetry = paymentLookupRetry;
        this.paymentLookupCircuitBreaker = paymentLookupCircuitBreaker;
        this.paymentApprovalRateLimiter = paymentApprovalRateLimiter;
        this.paymentLookupRateLimiter = paymentLookupRateLimiter;
    }

    @Override
    public PgApproval approve(String paymentKey, String pgOrderId, BigDecimal amount, String idempotencyKey) {
        return executeWithResilience( // <--
            () -> requestApproval(paymentKey, pgOrderId, amount, idempotencyKey),
            paymentApprovalRateLimiter,
            paymentApprovalRetry,
            paymentApprovalCircuitBreaker,
            "토스 결제 승인 요청 한도를 초과했습니다. 잠시 후 재시도합니다.",
            "토스 결제 승인 요청이 일시적으로 차단되었습니다."
        );
    }

    private PgApproval requestApproval(
        String paymentKey,
        String pgOrderId,
        BigDecimal amount,
        String idempotencyKey
    ) {
        try {
            TossPaymentResponse response = tossRestClient.post()
                .uri("/v1/payments/confirm")
                .header("idempotency-key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TossConfirmRequest(paymentKey, pgOrderId, amount))
                .retrieve()
                .body(TossPaymentResponse.class);

            if (response == null || !"DONE".equals(response.status())) {
                throw new PaymentGatewayException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    PaymentGatewayFailureType.UNCERTAIN,
                    "토스 승인 결과를 확인할 수 없습니다."
                );
            }

            return new PgApproval(
                response.paymentKey(),
                response.orderId(),
                response.totalAmount()
            );
        } catch (RestClientResponseException exception) {
            throw toPaymentGatewayException(exception);
        } catch (ResourceAccessException exception) {
            throw new PaymentGatewayException(
                HttpStatus.SERVICE_UNAVAILABLE,
                PaymentGatewayFailureType.UNCERTAIN,
                "토스 결제 서버에 연결할 수 없습니다."
            );
        }
    }
    @Override
    public PgPayment getPayment(String paymentKey) {
        return executeWithResilience( // <--
            () -> requestPayment(paymentKey),
            paymentLookupRateLimiter,
            paymentLookupRetry,
            paymentLookupCircuitBreaker,
            "토스 결제 조회 요청 한도를 초과했습니다. 잠시 후 재시도합니다.",
            "토스 결제 조회 요청이 일시적으로 차단되었습니다."
        );
    }


    // Toss 호출의 Resilience4j 실행 순서와 예외 변환을 공통적으로 처리
    private <T> T executeWithResilience(
        Supplier<T> supplier,
        RateLimiter rateLimiter,
        Retry retry,
        CircuitBreaker circuitBreaker,
        String rateLimitMessage,
        String circuitOpenMessage
    ) {
        Supplier<T> rateLimitedSupplier = RateLimiter.decorateSupplier(
            rateLimiter,
            supplier
        );
        Supplier<T> retrySupplier = Retry.decorateSupplier(
            retry,
            rateLimitedSupplier
        );
        Supplier<T> circuitBreakerSupplier = CircuitBreaker.decorateSupplier(
            circuitBreaker,
            retrySupplier
        );

        try {
            return circuitBreakerSupplier.get();
        } catch (RequestNotPermitted exception) {
            throw new PaymentGatewayException(
                HttpStatus.SERVICE_UNAVAILABLE,
                PaymentGatewayFailureType.UNCERTAIN,
                rateLimitMessage
            );
        } catch (CallNotPermittedException exception) {
            throw new PaymentGatewayException(
                HttpStatus.SERVICE_UNAVAILABLE,
                PaymentGatewayFailureType.UNCERTAIN,
                circuitOpenMessage
            );
        }
    }

    private PgPayment requestPayment(String paymentKey) {
        try {
            TossPaymentResponse response = tossRestClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentResponse.class);

            if (response == null) {
                throw new PaymentGatewayException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    PaymentGatewayFailureType.UNCERTAIN,
                    "토스 결제 조회 결과를 확인할 수 없습니다."
                );
            }

            return new PgPayment(
                response.paymentKey(),
                response.orderId(),
                response.totalAmount(),
                PgPaymentStatus.fromTossStatus(response.status())
            );
        } catch (RestClientResponseException exception) {
            throw toPaymentGatewayException(exception);
        } catch (ResourceAccessException exception) {
            throw new PaymentGatewayException(
                HttpStatus.SERVICE_UNAVAILABLE,
                PaymentGatewayFailureType.UNCERTAIN,
                "토스 결제 서버에 연결할 수 없습니다."
            );
        }
    }

    //상태 유형 결정 메서드
    private PaymentGatewayException toPaymentGatewayException(
        RestClientResponseException exception
    ) {
        try {
            TossErrorResponse errorResponse = objectMapper.readValue(
                exception.getResponseBodyAsString(),
                TossErrorResponse.class
            );


            HttpStatus status = exception.getStatusCode().is5xxServerError()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.CONFLICT;

            return new PaymentGatewayException(
                status,
                resolveFailureType(exception, errorResponse),
                errorResponse.message()
            );
        } catch (JsonProcessingException ignored) {
            return new PaymentGatewayException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                PaymentGatewayFailureType.UNCERTAIN,
                "토스 결제 승인 중 알 수 없는 오류가 발생했습니다."
            );
        }
    }

    private PaymentGatewayFailureType resolveFailureType(
        RestClientResponseException exception,
        TossErrorResponse errorResponse
    ) {
        int statusCode = exception.getStatusCode().value();

        if (exception.getStatusCode().is5xxServerError()
            || statusCode == HttpStatus.REQUEST_TIMEOUT.value()
            || statusCode == HttpStatus.TOO_MANY_REQUESTS.value()
            || "ALREADY_PROCESSED_PAYMENT".equals(errorResponse.code())
        ) {
            return PaymentGatewayFailureType.UNCERTAIN;
        }

        return PaymentGatewayFailureType.DEFINITIVE;
    }
}
