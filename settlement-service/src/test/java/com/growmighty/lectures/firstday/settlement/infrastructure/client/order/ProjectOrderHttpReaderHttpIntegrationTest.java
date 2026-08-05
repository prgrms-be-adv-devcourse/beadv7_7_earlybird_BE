package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ProjectOrderHttpReaderHttpIntegrationTest {

    private HttpServer server;
    private ExecutorService executor;
    private volatile String requestMethod;
    private volatile String requestBody;
    private volatile Duration responseDelay = Duration.ZERO;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext(ProjectOrderHttpReader.PROJECT_ORDERS_PATH, this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    @DisplayName("실제 HTTP 요청으로 프로젝트별 주문 결제금액을 조회한다")
    void readsProjectOrdersOverHttp() {
        ProjectOrderReader reader = reader(Duration.ofSeconds(1));

        List<ProjectOrders> result = reader.findProjectOrders(Set.of(102L, 101L));

        assertThat(requestMethod).isEqualTo("POST");
        assertThat(requestBody).isEqualTo("{\"projectIds\":[101,102]}");
        assertThat(result).containsExactly(
                new ProjectOrders(101L, List.of(
                        new OrderPayment(1001L, Money.wons(50_000))
                )),
                new ProjectOrders(102L, List.of())
        );
    }

    @Test
    @DisplayName("Order 응답 제한시간이 지나면 정산 입력 조회 불가로 번역한다")
    void translatesReadTimeout() {
        responseDelay = Duration.ofMillis(300);
        ProjectOrderReader reader = reader(Duration.ofMillis(50));

        assertThatThrownBy(() -> reader.findProjectOrders(Set.of(101L)))
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE
                        ));
    }

    private ProjectOrderReader reader(Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(readTimeout);
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new ProjectOrderHttpReader(restClient);
    }

    private void respond(HttpExchange exchange) throws IOException {
        requestMethod = exchange.getRequestMethod();
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            Thread.sleep(responseDelay);
            byte[] body = """
                    {
                      "success": true,
                      "data": {
                        "projects": [
                          {
                            "projectId": 101,
                            "orders": [
                              {"orderId": 1001, "paymentAmount": 50000}
                            ]
                          },
                          {
                            "projectId": 102,
                            "orders": []
                          }
                        ]
                      },
                      "error": null
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }
}
