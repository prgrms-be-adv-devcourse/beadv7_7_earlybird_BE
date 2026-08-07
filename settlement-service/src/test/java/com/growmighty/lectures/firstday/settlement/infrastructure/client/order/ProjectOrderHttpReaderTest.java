// TODO(settlement-plan): Verify complete recovery mapping and centralize contract-failure cases at the adapter interface.
package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

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
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.io.IOException;
import java.util.HashSet;
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

class ProjectOrderHttpReaderTest {

    private static final String BASE_URL = "http://order-service";

    private MockRestServiceServer server;
    private ProjectOrderReader reader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        reader = new ProjectOrderHttpReader(builder.build());
    }

    @Test
    @DisplayName("프로젝트별 주문 식별자와 결제금액을 내부 Order 입력으로 변환한다")
    void readsProjectOrderPayments() {
        server.expect(once(), requestTo(BASE_URL + ProjectOrderHttpReader.PROJECT_ORDERS_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "projectIds": [101, 102]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": {
                            "projects": [
                              {
                                "projectId": 102,
                                "orders": []
                              },
                              {
                                "projectId": 101,
                                "orders": [
                                  {
                                    "orderId": 1001,
                                    "paymentAmount": 50000,
                                    "ignoredField": "not part of settlement contract"
                                  },
                                  {
                                    "orderId": 1002,
                                    "paymentAmount": 30000
                                  }
                                ]
                              }
                            ]
                          },
                          "error": null
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ProjectOrders> result = reader.findProjectOrders(Set.of(102L, 101L));

        assertThat(result).containsExactly(
                new ProjectOrders(102L, List.of()),
                new ProjectOrders(101L, List.of(
                        new OrderPayment(1001L, Money.wons(50_000)),
                        new OrderPayment(1002L, Money.wons(30_000))
                ))
        );
        server.verify();
    }

    @Test
    @DisplayName("0원 주문 결제금액을 값의 의미를 바꾸지 않고 변환한다")
    void preservesZeroPaymentAmount() {
        expectSuccess("""
                {
                  "success": true,
                  "data": {
                    "projects": [
                      {
                        "projectId": 101,
                        "orders": [
                          {"orderId": 1001, "paymentAmount": 0}
                        ]
                      }
                    ]
                  },
                  "error": null
                }
                """);

        assertThat(reader.findProjectOrders(Set.of(101L)))
                .containsExactly(new ProjectOrders(
                        101L,
                        List.of(new OrderPayment(1001L, Money.wons(0)))
                ));
        server.verify();
    }

    @Test
    @DisplayName("실패 envelope를 Order 정산 입력 조회 불가로 번역한다")
    void rejectsFailureEnvelope() {
        expectSuccess("""
                {
                  "success": false,
                  "data": null,
                  "error": {"message": "Order 내부 오류"}
                }
                """);

        assertUnavailable(() -> reader.findProjectOrders(Set.of(101L)));
        server.verify();
    }

    @Test
    @DisplayName("성공 데이터와 오류가 함께 있는 모순 envelope를 거부한다")
    void rejectsSuccessEnvelopeContainingError() {
        expectSuccess("""
                {
                  "success": true,
                  "data": {"projects": []},
                  "error": {"message": "Order 원문 내부 오류"}
                }
                """);

        assertUnavailable(() -> reader.findProjectOrders(Set.of(101L)));
        server.verify();
    }

    @Test
    @DisplayName("프로젝트 목록이 누락된 성공 envelope를 거부한다")
    void rejectsMissingProjects() {
        expectSuccess("""
                {
                  "success": true,
                  "data": {},
                  "error": null
                }
                """);

        assertUnavailable(() -> reader.findProjectOrders(Set.of(101L)));
        server.verify();
    }

    @Test
    @DisplayName("필수 주문 결제금액이 누락되면 응답 전체를 거부한다")
    void rejectsMissingRequiredFields() {
        expectSuccess("""
                {
                  "success": true,
                  "data": {
                    "projects": [
                      {
                        "projectId": 101,
                        "orders": [
                          {"orderId": 1001}
                        ]
                      }
                    ]
                  },
                  "error": null
                }
                """);

        assertUnavailable(() -> reader.findProjectOrders(Set.of(101L)));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "10.5"})
    @DisplayName("음수 또는 원 단위 정수가 아닌 결제금액을 거부한다")
    void rejectsInvalidPaymentAmount(String paymentAmount) {
        expectSuccess("""
                {
                  "success": true,
                  "data": {
                    "projects": [
                      {
                        "projectId": 101,
                        "orders": [
                          {"orderId": 1001, "paymentAmount": %s}
                        ]
                      }
                    ]
                  },
                  "error": null
                }
                """.formatted(paymentAmount));

        assertUnavailable(() -> reader.findProjectOrders(Set.of(101L)));
        server.verify();
    }

    @Test
    @DisplayName("한 프로젝트 안의 중복 주문 식별자를 거부한다")
    void rejectsDuplicateOrderId() {
        expectSuccess("""
                {
                  "success": true,
                  "data": {
                    "projects": [
                      {
                        "projectId": 101,
                        "orders": [
                          {"orderId": 1001, "paymentAmount": 10000},
                          {"orderId": 1001, "paymentAmount": 20000}
                        ]
                      }
                    ]
                  },
                  "error": null
                }
                """);

        assertUnavailable(() -> reader.findProjectOrders(Set.of(101L)));
        server.verify();
    }

    @Test
    @DisplayName("Order HTTP 오류를 안전한 정산 오류로 격리한다")
    void translatesHttpError() {
        server.expect(once(), requestTo(BASE_URL + ProjectOrderHttpReader.PROJECT_ORDERS_PATH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "success": false,
                                  "error": {"message": "Order 저장소 접속 실패"}
                                }
                                """));

        assertThatThrownBy(() -> reader.findProjectOrders(Set.of(101L)))
                .isInstanceOfSatisfying(SettlementException.class, exception -> {
                    assertThat(exception.errorCode())
                            .isEqualTo(SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE);
                    assertThat(exception.getMessage())
                            .doesNotContain("Order 저장소 접속 실패");
                });
        server.verify();
    }

    @Test
    @DisplayName("해석할 수 없는 Order 응답을 안전한 정산 오류로 격리한다")
    void translatesMalformedResponse() {
        server.expect(once(), requestTo(BASE_URL + ProjectOrderHttpReader.PROJECT_ORDERS_PATH))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertUnavailable(() -> reader.findProjectOrders(Set.of(101L)));
        server.verify();
    }

    @Test
    @DisplayName("Order 연결 실패를 안전한 정산 오류로 격리한다")
    void translatesConnectionFailure() {
        server.expect(once(), requestTo(BASE_URL + ProjectOrderHttpReader.PROJECT_ORDERS_PATH))
                .andRespond(request -> {
                    throw new IOException("Order 연결 원문 오류");
                });

        assertUnavailable(() -> reader.findProjectOrders(Set.of(101L)));
        server.verify();
    }

    @Test
    @DisplayName("빈·유효하지 않은·100개 초과 프로젝트 요청을 HTTP 호출 전에 거부한다")
    void rejectsInvalidRequest() {
        Set<Long> tooManyProjectIds = new HashSet<>();
        for (long projectId = 1; projectId <= 101; projectId++) {
            tooManyProjectIds.add(projectId);
        }

        assertUnavailable(() -> reader.findProjectOrders(Set.of()));
        assertUnavailable(() -> reader.findProjectOrders(Set.of(0L)));
        assertUnavailable(() -> reader.findProjectOrders(tooManyProjectIds));
        assertUnavailable(() -> reader.findProjectOrders(null));
        server.verify();
    }

    private void expectSuccess(String body) {
        server.expect(once(), requestTo(BASE_URL + ProjectOrderHttpReader.PROJECT_ORDERS_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static void assertUnavailable(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE));
    }
}
