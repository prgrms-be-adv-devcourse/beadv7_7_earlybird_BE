package com.growmighty.lectures.firstday.settlement.infrastructure.client.payment;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationStatus.COMPLETED;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class ProjectPaymentRefundHttpGateway
        implements ProjectPaymentCancellationGateway {

    static final String PAYMENT_REFUND_PATH =
            "/internal/v1/payments/orders/{orderId}/refund";

    private final RestClient restClient;

    public ProjectPaymentRefundHttpGateway(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "Payment HTTP 클라이언트는 필수입니다.");
    }

    @Override
    public List<ProjectPaymentCancellationResult> cancel(
            List<ProjectPaymentCancellationRequest> requests
    ) {
        List<ProjectPaymentCancellationRequest> copiedRequests = validateRequests(requests);
        return copiedRequests.stream()
                .map(this::refund)
                .toList();
    }

    private ProjectPaymentCancellationResult refund(
            ProjectPaymentCancellationRequest request
    ) {
        PaymentRefundEnvelope response;
        try {
            response = restClient.post()
                    .uri(PAYMENT_REFUND_PATH, request.orderId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new PaymentRefundRequest(PaymentRefundReason.from(request.reason())))
                    .retrieve()
                    .body(PaymentRefundEnvelope.class);
        } catch (RestClientException exception) {
            throw new SettlementException(
                    PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE,
                    exception
            );
        }

        validateResponse(response);
        return new ProjectPaymentCancellationResult(request.orderId(), COMPLETED);
    }

    private static List<ProjectPaymentCancellationRequest> validateRequests(
            List<ProjectPaymentCancellationRequest> requests
    ) {
        try {
            List<ProjectPaymentCancellationRequest> copiedRequests = List.copyOf(requests);
            Set<Long> orderIds = new HashSet<>();
            if (copiedRequests.stream().anyMatch(request -> !orderIds.add(request.orderId()))) {
                throw new IllegalArgumentException("Payment 환불 주문 식별자는 중복될 수 없습니다.");
            }
            return copiedRequests;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SettlementException(
                    PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE,
                    exception
            );
        }
    }

    private static void validateResponse(PaymentRefundEnvelope response) {
        if (response == null
                || !response.success()
                || response.data() == null
                || response.error() != null) {
            throw new SettlementException(PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE);
        }
        try {
            PaymentRefundResponse data = response.data();
            if (data.refundId() == null || data.refundId() <= 0
                    || data.paymentId() == null || data.paymentId() <= 0
                    || data.amount() == null
                    || data.amount().compareTo(BigDecimal.ZERO) <= 0
                    || !isWholeWon(data.amount())
                    || !"COMPLETED".equals(data.status())) {
                throw new IllegalArgumentException("Payment 환불 성공 응답 계약을 위반했습니다.");
            }
        } catch (RuntimeException exception) {
            throw new SettlementException(
                    PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE,
                    exception
            );
        }
    }

    private static boolean isWholeWon(BigDecimal amount) {
        try {
            amount.setScale(0, RoundingMode.UNNECESSARY);
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private record PaymentRefundRequest(PaymentRefundReason reason) {
    }

    private record PaymentRefundEnvelope(
            boolean success,
            PaymentRefundResponse data,
            Object error
    ) {
    }

    private record PaymentRefundResponse(
            Long refundId,
            Long paymentId,
            BigDecimal amount,
            String status
    ) {
    }

    private enum PaymentRefundReason {
        GOAL_FAILED,
        USER_CANCEL;

        private static PaymentRefundReason from(ProjectCancellationReason reason) {
            return switch (reason) {
                case PROJECT_FAILED -> GOAL_FAILED;
                case PROJECT_CANCELLED -> USER_CANCEL;
            };
        }
    }
}
