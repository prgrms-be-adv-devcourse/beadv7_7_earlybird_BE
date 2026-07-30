package com.growmighty.lectures.firstday.settlement.infrastructure.client.payment;

import static com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus.COMPLETED;
import static com.growmighty.lectures.firstday.settlement.domain.ProjectCancellationReason.PROJECT_CANCELLED;
import static com.growmighty.lectures.firstday.settlement.domain.ProjectCancellationReason.PROJECT_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ProjectPaymentRefundHttpGatewayHttpIntegrationTest {

    private static final String PAYMENT_ORDERS_PATH = "/internal/v1/payments/orders/";

    private final List<ObservedRequest> observedRequests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private ExecutorService executor;
    private volatile Duration responseDelay = Duration.ZERO;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext(PAYMENT_ORDERS_PATH, this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
        observedRequests.clear();
    }

    @Test
    @DisplayName("실제 HTTP 요청으로 실패·취소 프로젝트 주문을 순차 환불한다")
    void refundsProjectOrdersOverHttp() {
        ProjectPaymentCancellationGateway gateway = gateway(Duration.ofSeconds(1));

        List<ProjectPaymentCancellationResult> results = gateway.cancel(List.of(
                new ProjectPaymentCancellationRequest(
                        1001L,
                        PROJECT_FAILED,
                        "cancel-1001"
                ),
                new ProjectPaymentCancellationRequest(
                        1002L,
                        PROJECT_CANCELLED,
                        "cancel-1002"
                )
        ));

        assertThat(observedRequests).containsExactly(
                new ObservedRequest(
                        "POST",
                        "/internal/v1/payments/orders/1001/refund",
                        "{\"reason\":\"GOAL_FAILED\"}"
                ),
                new ObservedRequest(
                        "POST",
                        "/internal/v1/payments/orders/1002/refund",
                        "{\"reason\":\"USER_CANCEL\"}"
                )
        );
        assertThat(results).containsExactly(
                new ProjectPaymentCancellationResult(1001L, COMPLETED),
                new ProjectPaymentCancellationResult(1002L, COMPLETED)
        );
    }

    @Test
    @DisplayName("Payment 응답 제한시간이 지나면 결제 취소 결과 확인 불가로 번역한다")
    void translatesReadTimeout() {
        responseDelay = Duration.ofMillis(300);
        ProjectPaymentCancellationGateway gateway = gateway(Duration.ofMillis(50));

        assertThatThrownBy(() -> gateway.cancel(List.of(
                new ProjectPaymentCancellationRequest(
                        1001L,
                        PROJECT_FAILED,
                        "cancel-1001"
                )
        )))
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                SettlementErrorCode.PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE
                        ));
    }

    private ProjectPaymentCancellationGateway gateway(Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(readTimeout);
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new ProjectPaymentRefundHttpGateway(restClient);
    }

    private void respond(HttpExchange exchange) throws IOException {
        String requestBody = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );
        observedRequests.add(new ObservedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                requestBody
        ));
        try {
            Thread.sleep(responseDelay);
            byte[] body = """
                    {
                      "success": true,
                      "data": {
                        "refundId": 11,
                        "paymentId": 21,
                        "amount": 50000,
                        "status": "COMPLETED"
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

    private record ObservedRequest(String method, String path, String body) {
    }
}
