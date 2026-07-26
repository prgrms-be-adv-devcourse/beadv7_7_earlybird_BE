package com.growmighty.lectures.firstday.settlement.infrastructure.client.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.config.TossPayoutClientConfig;
import com.growmighty.lectures.firstday.settlement.config.TossPayoutProperties;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class TossPayoutGatewayHttpIntegrationTest {

    private static final String SECRET_KEY = "test_sk_example";
    private static final String SECURITY_KEY_HEX = "01".repeat(32);
    private static final byte[] SECURITY_KEY = HexFormat.of().parseHex(SECURITY_KEY_HEX);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T01:02:03Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<RecordedRequest> requests = new ArrayList<>();
    private HttpServer server;
    private ExecutorService executor;
    private StubBehavior behavior;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/v2/payouts", this::handle);
        server.start();
        behavior = request -> encrypted(200, payoutResponse("toss-payout-http-1", "REQUESTED", null));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    @DisplayName("실제 HTTP 요청으로 인증·보안·예약 지급 계약을 전송하고 성공 응답을 복호화한다")
    void exchangesEncryptedScheduledPayoutOverHttp() throws Exception {
        PayoutGatewayResult result = gateway(Duration.ofSeconds(1))
                .requestScheduledPayout(request());

        assertThat(result).isEqualTo(new PayoutGatewayResult.Accepted(
                "toss-payout-http-1",
                PayoutAttemptStatus.REQUESTED,
                null
        ));
        assertThat(requests).hasSize(1);
        assertHttpContract(requests.getFirst());
    }

    @Test
    @DisplayName("암호화된 최종 실패 응답에서 외부 식별자와 오류 코드를 보존한다")
    void returnsFinalFailureWithExternalId() {
        behavior = request -> encrypted(
                200,
                payoutResponse("toss-payout-http-failed", "FAILED", "PAYOUT_FAILED")
        );

        PayoutGatewayResult result = gateway(Duration.ofSeconds(1))
                .requestScheduledPayout(request());

        assertThat(result).isEqualTo(new PayoutGatewayResult.Accepted(
                "toss-payout-http-failed",
                PayoutAttemptStatus.FAILED,
                "PAYOUT_FAILED"
        ));
    }

    @Test
    @DisplayName("암호화된 일시 오류 응답을 명시적인 요청 거절로 반환한다")
    void returnsTransientHttpFailure() {
        behavior = request -> encrypted(503, errorResponse("COMMON_ERROR"));

        PayoutGatewayResult result = gateway(Duration.ofSeconds(1))
                .requestScheduledPayout(request());

        assertThat(result).isEqualTo(new PayoutGatewayResult.Rejected("COMMON_ERROR"));
    }

    @Test
    @DisplayName("HTTP 응답 제한시간이 지나면 지급 결과를 확정하지 않는다")
    void leavesTimedOutRequestUnresolved() {
        behavior = request -> {
            Thread.sleep(300);
            return encrypted(200, payoutResponse("late-payout", "REQUESTED", null));
        };

        assertThatThrownBy(() -> gateway(Duration.ofMillis(50))
                .requestScheduledPayout(request()))
                .isInstanceOf(PayoutGatewayException.class)
                .hasMessageContaining("결과를 확인하지 못했습니다");
    }

    @Test
    @DisplayName("같은 멱등키로 다시 호출하면 HTTP 대역에서 동일한 지급 결과를 받는다")
    void repeatsRequestWithSameIdempotencyKey() {
        Map<String, String> payoutsByIdempotencyKey = new ConcurrentHashMap<>();
        AtomicInteger createdPayouts = new AtomicInteger();
        behavior = recorded -> {
            String idempotencyKey = recorded.headers().getFirst(
                    TossPayoutGateway.IDEMPOTENCY_KEY_HEADER
            );
            String payoutId = payoutsByIdempotencyKey.computeIfAbsent(
                    idempotencyKey,
                    key -> "toss-payout-http-" + createdPayouts.incrementAndGet()
            );
            return encrypted(200, payoutResponse(payoutId, "REQUESTED", null));
        };
        TossPayoutGateway gateway = gateway(Duration.ofSeconds(1));

        PayoutGatewayResult first = gateway.requestScheduledPayout(request());
        PayoutGatewayResult second = gateway.requestScheduledPayout(request());

        assertThat(first).isEqualTo(second);
        assertThat(requests).hasSize(2);
        assertThat(requests)
                .extracting(recorded -> recorded.headers().getFirst(
                        TossPayoutGateway.IDEMPOTENCY_KEY_HEADER
                ))
                .containsOnly("idempotency-http-1");
        assertThat(createdPayouts).hasValue(1);
    }

    private TossPayoutGateway gateway(Duration readTimeout) {
        TossPayoutProperties properties = new TossPayoutProperties(
                true,
                SECRET_KEY,
                SECURITY_KEY_HEX,
                baseUrl(),
                Duration.ofSeconds(1),
                readTimeout
        );
        RestClient restClient = new TossPayoutClientConfig().tossPayoutRestClient(properties);
        return new TossPayoutGateway(
                restClient,
                new TossPayoutJweCodec(SECURITY_KEY, CLOCK),
                OBJECT_MAPPER,
                SECRET_KEY
        );
    }

    private void handle(HttpExchange exchange) throws IOException {
        RecordedRequest recorded = new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        );
        synchronized (requests) {
            requests.add(recorded);
        }

        try {
            StubResponse response = behavior.respond(recorded);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.statusCode(), body.length);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private void assertHttpContract(RecordedRequest recorded) throws Exception {
        assertThat(recorded.method()).isEqualTo("POST");
        assertThat(recorded.path()).isEqualTo("/v2/payouts");
        assertThat(recorded.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(
                "Basic " + Base64.getEncoder().encodeToString(
                        (SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8)
                )
        );
        assertThat(recorded.headers().getFirst(TossPayoutGateway.SECURITY_MODE_HEADER))
                .isEqualTo(TossPayoutGateway.SECURITY_MODE_ENCRYPTION);
        assertThat(recorded.headers().getFirst(TossPayoutGateway.IDEMPOTENCY_KEY_HEADER))
                .isEqualTo("idempotency-http-1");

        JWEObject jweObject = JWEObject.parse(recorded.body());
        assertThat(jweObject.getHeader().getAlgorithm()).isEqualTo(JWEAlgorithm.DIR);
        assertThat(jweObject.getHeader().getEncryptionMethod()).isEqualTo(EncryptionMethod.A256GCM);
        assertThat(jweObject.getHeader().getCustomParam("iat"))
                .isEqualTo("2026-07-26T10:02:03+09:00");
        assertThat(jweObject.getHeader().getCustomParam("nonce").toString()).isNotBlank();
        jweObject.decrypt(new DirectDecrypter(SECURITY_KEY));

        JsonNode root = OBJECT_MAPPER.readTree(jweObject.getPayload().toString());
        assertThat(root).hasSize(1);
        JsonNode payout = root.get(0);
        assertThat(payout.path("refPayoutId").asString()).isEqualTo("ref-payout-http-1");
        assertThat(payout.path("destination").asString()).isEqualTo("seller-http-1");
        assertThat(payout.path("scheduleType").asString()).isEqualTo("SCHEDULED");
        assertThat(payout.path("payoutDate").asString()).isEqualTo("2026-08-03");
        assertThat(payout.path("amount").path("currency").asString()).isEqualTo("KRW");
        assertThat(payout.path("amount").path("value").longValue()).isEqualTo(10_000L);
        assertThat(payout.path("transactionDescription").asString()).isEqualTo("얼리버드");
    }

    private String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    private static StubResponse encrypted(int statusCode, String plainText) {
        try {
            JWEObject jweObject = new JWEObject(
                    new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).build(),
                    new Payload(plainText)
            );
            jweObject.encrypt(new DirectEncrypter(SECURITY_KEY));
            return new StubResponse(statusCode, jweObject.serialize());
        } catch (Exception exception) {
            throw new AssertionError("HTTP 대역 응답을 암호화하지 못했습니다.", exception);
        }
    }

    private static String payoutResponse(String payoutId, String status, String errorCode) {
        String error = errorCode == null
                ? "null"
                : "{\"code\":\"" + errorCode + "\",\"message\":\"실패\"}";
        return """
                {
                  "entityBody": {
                    "items": [
                      {
                        "id": "%s",
                        "status": "%s",
                        "error": %s
                      }
                    ]
                  }
                }
                """.formatted(payoutId, status, error);
    }

    private static String errorResponse(String errorCode) {
        return """
                {
                  "error": {
                    "code": "%s",
                    "message": "일시 오류"
                  }
                }
                """.formatted(errorCode);
    }

    private static ScheduledPayoutRequest request() {
        return new ScheduledPayoutRequest(
                "ref-payout-http-1",
                "seller-http-1",
                LocalDate.of(2026, 8, 3),
                Money.wons(10_000),
                "얼리버드",
                "idempotency-http-1"
        );
    }

    @FunctionalInterface
    private interface StubBehavior {

        StubResponse respond(RecordedRequest request) throws InterruptedException;
    }

    private record RecordedRequest(
            String method,
            String path,
            Headers headers,
            String body
    ) {
    }

    private record StubResponse(int statusCode, String body) {
    }
}
