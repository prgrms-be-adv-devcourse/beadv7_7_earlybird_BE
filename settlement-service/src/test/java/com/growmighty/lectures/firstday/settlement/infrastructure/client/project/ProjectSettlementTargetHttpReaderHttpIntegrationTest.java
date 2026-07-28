package com.growmighty.lectures.firstday.settlement.infrastructure.client.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ProjectSettlementTargetHttpReaderHttpIntegrationTest {

    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext(
                ProjectSettlementTargetHttpReader.SETTLEMENT_TARGETS_PATH,
                this::respondAfterReadTimeout
        );
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Project 응답 제한시간이 지나면 정산 대상 조회 불가로 번역한다")
    void translatesReadTimeout() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofMillis(50));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl())
                .requestFactory(requestFactory)
                .build();
        ProjectSettlementTargetReader reader = new ProjectSettlementTargetHttpReader(restClient);

        assertThatThrownBy(() -> reader.findSettlementTargets(YearMonth.of(2026, 7)))
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE
                        ));
    }

    private void respondAfterReadTimeout(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(300);
            byte[] body = """
                    {
                      "success": true,
                      "data": [],
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
