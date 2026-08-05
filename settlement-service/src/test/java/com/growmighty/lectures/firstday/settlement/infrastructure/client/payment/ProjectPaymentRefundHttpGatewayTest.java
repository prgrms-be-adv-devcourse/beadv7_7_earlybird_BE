package com.growmighty.lectures.firstday.settlement.infrastructure.client.payment;

import static com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationStatus.COMPLETED;
import static com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason.PROJECT_CANCELLED;
import static com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason.PROJECT_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProjectPaymentRefundHttpGatewayTest {

    private static final String BASE_URL = "http://payment-service";

    private MockRestServiceServer server;
    private ProjectPaymentCancellationGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new ProjectPaymentRefundHttpGateway(builder.build());
    }

    @Test
    @DisplayName("실패·취소 프로젝트 사유를 Payment 환불 사유로 변환해 순서대로 호출한다")
    void refundsFailedAndCancelledProjectOrders() {
        expectCompletedRefund(1001L, "GOAL_FAILED", 11L, 21L, "50000");
        expectCompletedRefund(1002L, "USER_CANCEL", 12L, 22L, "30000.00");

        List<ProjectPaymentCancellationResult> results = gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001"),
                request(1002L, PROJECT_CANCELLED, "cancel-1002")
        ));

        assertThat(results).containsExactly(
                new ProjectPaymentCancellationResult(1001L, COMPLETED),
                new ProjectPaymentCancellationResult(1002L, COMPLETED)
        );
        server.verify();
    }

    @Test
    @DisplayName("빈 환불 요청은 Payment를 호출하지 않고 빈 결과를 반환한다")
    void returnsEmptyResultWithoutHttpCall() {
        assertThat(gateway.cancel(List.of())).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("중복 주문 환불 요청은 Payment를 호출하기 전에 거부한다")
    void rejectsDuplicateOrderIdBeforeHttpCall() {
        assertUnavailable(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001"),
                request(1001L, PROJECT_FAILED, "cancel-1001-duplicate")
        )));
        server.verify();
    }

    @Test
    @DisplayName("실패 envelope를 결제 취소 결과 확인 불가로 번역한다")
    void rejectsFailureEnvelope() {
        expectSuccess(1001L, """
                {
                  "success": false,
                  "data": null,
                  "error": {"message": "Payment 내부 오류"}
                }
                """);

        assertUnavailable(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001")
        )));
        server.verify();
    }

    @Test
    @DisplayName("성공 데이터와 오류가 함께 있는 모순 envelope를 거부한다")
    void rejectsSuccessEnvelopeContainingError() {
        expectSuccess(1001L, """
                {
                  "success": true,
                  "data": {
                    "refundId": 11,
                    "paymentId": 21,
                    "amount": 50000,
                    "status": "COMPLETED"
                  },
                  "error": {"message": "Payment 원문 내부 오류"}
                }
                """);

        assertUnavailable(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001")
        )));
        server.verify();
    }

    @Test
    @DisplayName("필수 환불 식별자가 누락되면 응답을 거부한다")
    void rejectsMissingRequiredIdentifier() {
        expectSuccess(1001L, """
                {
                  "success": true,
                  "data": {
                    "refundId": null,
                    "paymentId": 21,
                    "amount": 50000,
                    "status": "COMPLETED"
                  },
                  "error": null
                }
                """);

        assertUnavailable(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001")
        )));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10.5"})
    @DisplayName("양수 원 단위 정수가 아닌 환불 금액을 거부한다")
    void rejectsInvalidRefundAmount(String amount) {
        expectSuccess(1001L, """
                {
                  "success": true,
                  "data": {
                    "refundId": 11,
                    "paymentId": 21,
                    "amount": %s,
                    "status": "COMPLETED"
                  },
                  "error": null
                }
                """.formatted(amount));

        assertUnavailable(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001")
        )));
        server.verify();
    }

    @Test
    @DisplayName("COMPLETED가 아닌 Payment 환불 상태를 성공으로 추정하지 않는다")
    void rejectsNonCompletedStatus() {
        expectSuccess(1001L, """
                {
                  "success": true,
                  "data": {
                    "refundId": 11,
                    "paymentId": 21,
                    "amount": 50000,
                    "status": "REQUESTED"
                  },
                  "error": null
                }
                """);

        assertUnavailable(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001")
        )));
        server.verify();
    }

    @Test
    @DisplayName("Payment HTTP 오류를 안전한 정산 오류로 격리한다")
    void translatesHttpError() {
        server.expect(once(), requestTo(refundUrl(1001L)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "success": false,
                                  "error": {"message": "Payment 저장소 접속 실패"}
                                }
                                """));

        assertThatThrownBy(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001")
        )))
                .isInstanceOfSatisfying(SettlementException.class, exception -> {
                    assertThat(exception.errorCode())
                            .isEqualTo(
                                    SettlementErrorCode.PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE
                            );
                    assertThat(exception.getMessage())
                            .doesNotContain("Payment 저장소 접속 실패");
                });
        server.verify();
    }

    @Test
    @DisplayName("해석할 수 없는 Payment 응답을 안전한 정산 오류로 격리한다")
    void translatesMalformedResponse() {
        server.expect(once(), requestTo(refundUrl(1001L)))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertUnavailable(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001")
        )));
        server.verify();
    }

    @Test
    @DisplayName("Payment 연결 실패를 안전한 정산 오류로 격리한다")
    void translatesConnectionFailure() {
        server.expect(once(), requestTo(refundUrl(1001L)))
                .andRespond(request -> {
                    throw new IOException("Payment 연결 원문 오류");
                });

        assertUnavailable(() -> gateway.cancel(List.of(
                request(1001L, PROJECT_FAILED, "cancel-1001")
        )));
        server.verify();
    }

    private void expectCompletedRefund(
            Long orderId,
            String reason,
            Long refundId,
            Long paymentId,
            String amount
    ) {
        server.expect(once(), requestTo(refundUrl(orderId)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "reason": "%s"
                        }
                        """.formatted(reason)))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": {
                            "refundId": %d,
                            "paymentId": %d,
                            "amount": %s,
                            "status": "COMPLETED"
                          },
                          "error": null
                        }
                        """.formatted(refundId, paymentId, amount), MediaType.APPLICATION_JSON));
    }

    private void expectSuccess(Long orderId, String body) {
        server.expect(once(), requestTo(refundUrl(orderId)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static ProjectPaymentCancellationRequest request(
            Long orderId,
            ProjectCancellationReason reason,
            String idempotencyKey
    ) {
        return new ProjectPaymentCancellationRequest(orderId, reason, idempotencyKey);
    }

    private static String refundUrl(Long orderId) {
        return BASE_URL + "/internal/v1/payments/orders/" + orderId + "/refund";
    }

    private static void assertUnavailable(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                SettlementErrorCode.PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE
                        ));
    }
}
