package com.growmighty.lectures.firstday.payment.infrastructure.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossConfirmRequest;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossErrorResponse;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "payment.gateway",
    havingValue = "toss"
)
public class TossPaymentGateway implements PaymentGateway {
    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public PgApproval approve(String paymentKey, String pgOrderId, BigDecimal amount, String idempotencyKey) {
        try {
            TossPaymentResponse response = tossRestClient.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TossConfirmRequest(paymentKey, pgOrderId, amount))
                .retrieve()
                .body(TossPaymentResponse.class);

            if (response == null) {
                throw new IllegalStateException("토스 승인 응답이 비어있습니다.");
            }

            if (!"DONE".equals(response.status())) {
                throw new IllegalStateException("토스 결제가 완료 상태가 아닙니다. status = " + response.status());
            }

            return new PgApproval(
                response.paymentKey(),
                response.orderId(),
                response.totalAmount()
            );
        } catch (RestClientResponseException e) {
            throw toTossPaymentException(e);
        } catch (ResourceAccessException e) {
            throw new TossPaymentException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TOSS_NETWORK_ERROR",
                "토스 결제 서버에 연결할 수 없습니다."
            );
        }

    }

    @Override
    public PgPayment getPayment(String paymentKey) {
        try {
            TossPaymentResponse response = tossRestClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentResponse.class);

            if (response == null) {
                throw new IllegalStateException("토스 결제 조회 응답이 비어있습니다.");
            }

            return new PgPayment(
                response.paymentKey(),
                response.orderId(),
                response.totalAmount(),
                toPgPaymentStatus(response.status())
            );
        } catch (RestClientResponseException e) {
            throw toTossPaymentException(e);
        } catch (ResourceAccessException e) {
            throw new TossPaymentException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "TOSS_NETWORK_ERROR",
                "토스 결제 서버에 연결할 수 없습니다."
            );
        }
    }

    /**
     * Toss 결제 상태를 Payment 내부 상태 표현으로 변환한다.
     */

    private PgPaymentStatus toPgPaymentStatus(String status) {
        if (status == null) {
            return PgPaymentStatus.PENDING;
        }

        return switch (status) {
            case "DONE" -> PgPaymentStatus.COMPLETED;
            case "ABORTED" -> PgPaymentStatus.FAILED;
            case "EXPIRED" -> PgPaymentStatus.EXPIRED;
            case "CANCELED" -> PgPaymentStatus.CANCELLED;
            default -> PgPaymentStatus.PENDING;
        };
    }

    @Override
    public void cancel(String paymentKey) {

    }

    private TossPaymentException toTossPaymentException(
        RestClientResponseException exception
    ) {
        try {
            TossErrorResponse error = objectMapper.readValue(
                exception.getResponseBodyAsString(),
                TossErrorResponse.class
            );

            HttpStatus status = exception.getStatusCode().is5xxServerError()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.CONFLICT;

            return new TossPaymentException(
                status,
                error.code(),
                error.message()
            );
        } catch (JsonProcessingException ignored) {
            return new TossPaymentException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TOSS_UNKNOWN_ERROR",
                "토스 결제 승인 중 알 수 없는 오류가 발생했습니다."
            );
        }
    }
}
