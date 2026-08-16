package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery.ProjectPayments;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
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

class OrderPaymentRecoveryHttpReaderTest {

    private static final String BASE_URL = "http://order-service";

    private MockRestServiceServer server;
    private OrderPaymentRecoveryHttpReader reader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        reader = new OrderPaymentRecoveryHttpReader(builder.build(), new ObjectMapper());
    }

    @Test
    @DisplayName("대상 월과 정렬된 프로젝트로 Order 결제 복구를 요청하고 완전한 응답을 변환한다")
    void recoversMonthlyProjectPayments() {
        server.expect(once(), requestTo(BASE_URL + OrderPaymentRecoveryHttpReader.PROJECT_PAYMENTS_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"projectIds":[101,102],"settlementMonth":"2026-07"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": {
                            "projects": [
                              {"projectId": 102, "orders": []},
                              {"projectId": 101, "orders": [
                                {"orderId": 1001, "pgOrderId": "PAY-1001", "paymentAmount": 50000, "orderStatus": "PAID"}
                              ]}
                            ]
                          },
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        OrderPaymentRecovery recovery = reader.recover(Set.of(102L, 101L), YearMonth.of(2026, 7));

        assertThat(recovery.projects()).containsExactly(
                new ProjectPayments(102L, List.of()),
                new ProjectPayments(101L, List.of(new OrderPayment(
                        1001L, "PAY-1001", Money.wons(50_000), OrderPayment.Status.PAID
                )))
        );
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"success\":true,\"data\":{\"projects\":[]},\"error\":null}",
            "{\"success\":true,\"data\":{\"projects\":[{\"projectId\":101,\"orders\":[{\"orderId\":1,\"pgOrderId\":\"PAY-1\",\"paymentAmount\":0,\"orderStatus\":\"PAID\"}]}]},\"error\":null}",
            "{\"success\":true,\"data\":{\"projects\":[{\"projectId\":101,\"orders\":[{\"orderId\":1,\"pgOrderId\":\"\",\"paymentAmount\":1,\"orderStatus\":\"PAID\"}]}]},\"error\":null}",
            "{\"success\":true,\"data\":{\"projects\":[{\"projectId\":101,\"orders\":[{\"orderId\":1,\"pgOrderId\":\"PAY-1\",\"paymentAmount\":1,\"orderStatus\":\"UNKNOWN\"}]}]},\"error\":null}",
            "{\"success\":true,\"data\":{\"projects\":[{\"projectId\":101,\"orders\":[{\"orderId\":1,\"pgOrderId\":\"PAY-1\",\"paymentAmount\":1,\"orderStatus\":\"PAID\"},{\"orderId\":1,\"pgOrderId\":\"PAY-2\",\"paymentAmount\":1,\"orderStatus\":\"PAID\"}]}]},\"error\":null}",
            "{\"success\":true,\"data\":{\"projects\":[{\"projectId\":101,\"orders\":[]},{\"projectId\":101,\"orders\":[]}]},\"error\":null}"
    })
    @DisplayName("계약에 맞지 않는 응답은 복구 검증 실패로 남긴다")
    void rejectsInvalidRecoveryResponse(String response) {
        expectSuccess(response);

        assertThatThrownBy(() -> reader.recover(Set.of(101L), YearMonth.of(2026, 7)))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    @DisplayName("Order 통신 오류는 정산 입력 조회 실패로 번역한다")
    void translatesHttpFailure() {
        server.expect(once(), requestTo(BASE_URL + OrderPaymentRecoveryHttpReader.PROJECT_PAYMENTS_PATH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> reader.recover(Set.of(101L), YearMonth.of(2026, 7)))
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE
                        ));
        server.verify();
    }

    @Test
    @DisplayName("비어 있거나 100개를 초과한 프로젝트 요청은 호출 전에 거부한다")
    void rejectsInvalidRequest() {
        assertThatThrownBy(() -> reader.recover(Set.of(), YearMonth.of(2026, 7)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.recover(Set.of(101L), null))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    private void expectSuccess(String body) {
        server.expect(once(), requestTo(BASE_URL + OrderPaymentRecoveryHttpReader.PROJECT_PAYMENTS_PATH))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
