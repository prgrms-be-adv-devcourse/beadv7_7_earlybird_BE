package com.growmighty.lectures.firstday.refund.infrastructure.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossErrorResponse;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossPaymentResponse;
import com.growmighty.lectures.firstday.refund.application.port.RefundGateway;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.infrastructure.dto.TossCancelRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "payment.gateway",
    havingValue = "toss"
)
public class TossRefundGateway implements RefundGateway {
    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public void refund(String paymentKey, RefundReason reason) {
        try {
            TossPaymentResponse response = tossRestClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TossCancelRequest(reason.name()))
                .retrieve()
                .body(TossPaymentResponse.class);

            if (response == null) {
                throw new IllegalStateException("토스 환불 응답이 비어있습니다.");
            }

            if (!"CANCELED".equals(response.status())) {
                throw new IllegalStateException("토스 결제가 취소 상태가 아닙니다. status = " + response.status());
            }
        } catch (RestClientResponseException e) {
            throw toRefundGatewayException(e);
        } catch (ResourceAccessException e) {
            throw new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "토스 결제 서버에 연결할 수 없습니다."
            );
        }
    }

    private BusinessException toRefundGatewayException(RestClientResponseException exception) {
        try {
            TossErrorResponse errorResponse = objectMapper.readValue(exception.getResponseBodyAsString(), TossErrorResponse.class);

            HttpStatus status = exception.getStatusCode().is5xxServerError()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.CONFLICT;

            return new BusinessException(
                status,
                errorResponse.message()
            );
        } catch (JsonProcessingException ignored) {
            return new BusinessException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "토스 환불 처리 중 알 수 없는 오류가 발생했습니다."
            );
        }
    }
}
