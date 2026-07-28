package com.growmighty.lectures.firstday.settlement.infrastructure.client.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class TossPayoutGatewayTest {

    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String SECRET_KEY = "test_sk_example";
    private static final byte[] SECURITY_KEY = java.util.HexFormat.of().parseHex("01".repeat(32));
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T01:02:03Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockRestServiceServer server;
    private TossPayoutGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPayoutGateway(
                builder.build(),
                new TossPayoutJweCodec(SECURITY_KEY, CLOCK),
                OBJECT_MAPPER,
                SECRET_KEY
        );
    }

    @Test
    @DisplayName("예약 지급을 토스 인증·보안·멱등 헤더와 암호화된 단건 배열로 요청한다")
    void requestsEncryptedScheduledPayout() throws Exception {
        server.expect(once(), requestTo(BASE_URL + "/v2/payouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(
                                (SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8)
                        )
                ))
                .andExpect(header(
                        TossPayoutGateway.SECURITY_MODE_HEADER,
                        TossPayoutGateway.SECURITY_MODE_ENCRYPTION
                ))
                .andExpect(header(TossPayoutGateway.IDEMPOTENCY_KEY_HEADER, "idempotency-1"))
                .andExpect(this::assertEncryptedScheduledPayoutBody)
                .andRespond(withSuccess(
                        encryptedResponse(successResponse("REQUESTED", null)),
                        MediaType.APPLICATION_JSON
                ));

        PayoutGatewayResult result = gateway.requestScheduledPayout(request());

        assertThat(result).isInstanceOfSatisfying(
                PayoutGatewayResult.Accepted.class,
                accepted -> {
                    assertThat(accepted.payoutId()).isEqualTo("toss-payout-1");
                    assertThat(accepted.status()).isEqualTo(PayoutAttemptStatus.REQUESTED);
                    assertThat(accepted.errorCode()).isNull();
                }
        );
        server.verify();
    }

    @Test
    @DisplayName("암호화된 HTTP 실패 응답의 오류 코드를 명시적 요청 거절로 반환한다")
    void returnsEncryptedRequestRejection() throws Exception {
        String failureResponse = """
                {
                  "version": "2022-11-16",
                  "traceId": "trace-1",
                  "error": {
                    "code": "INVALID_PAYOUT_DATE",
                    "message": "지급일이 올바르지 않습니다."
                  }
                }
                """;
        server.expect(once(), requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(encryptedResponse(failureResponse)));

        PayoutGatewayResult result = gateway.requestScheduledPayout(request());

        assertThat(result).isEqualTo(new PayoutGatewayResult.Rejected("INVALID_PAYOUT_DATE"));
        server.verify();
    }

    @Test
    @DisplayName("생성된 지급이 즉시 실패하면 외부 식별자와 실패 코드를 함께 반환한다")
    void returnsFailedPayoutResource() throws Exception {
        server.expect(once(), requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(withSuccess(
                        encryptedResponse(successResponse("FAILED", "PAYOUT_FAILED")),
                        MediaType.APPLICATION_JSON
                ));

        PayoutGatewayResult result = gateway.requestScheduledPayout(request());

        assertThat(result).isEqualTo(new PayoutGatewayResult.Accepted(
                "toss-payout-1",
                PayoutAttemptStatus.FAILED,
                "PAYOUT_FAILED"
        ));
        server.verify();
    }

    @Test
    @DisplayName("복호화할 수 없는 응답은 확정 결과로 만들지 않는다")
    void rejectsUnreadableResponse() {
        server.expect(once(), requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(withSuccess("not-a-jwe", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.requestScheduledPayout(request()))
                .isInstanceOf(PayoutGatewayException.class)
                .hasMessageContaining("결과를 확인하지 못했습니다");
        server.verify();
    }

    private void assertEncryptedScheduledPayoutBody(
            org.springframework.http.client.ClientHttpRequest request
    ) {
        try {
            String encryptedBody = ((MockClientHttpRequest) request).getBodyAsString();
            JWEObject jweObject = JWEObject.parse(encryptedBody);
            jweObject.decrypt(new DirectDecrypter(SECURITY_KEY));
            JsonNode root = OBJECT_MAPPER.readTree(jweObject.getPayload().toString());

            assertThat(root.isArray()).isTrue();
            assertThat(root).hasSize(1);
            JsonNode payout = root.get(0);
            assertThat(payout.path("refPayoutId").asString()).isEqualTo("ref-payout-1");
            assertThat(payout.path("destination").asString()).isEqualTo("seller-1");
            assertThat(payout.path("scheduleType").asString()).isEqualTo("SCHEDULED");
            assertThat(payout.path("payoutDate").asString()).isEqualTo("2026-08-03");
            assertThat(payout.path("amount").path("currency").asString()).isEqualTo("KRW");
            assertThat(payout.path("amount").path("value").longValue()).isEqualTo(10_000L);
            assertThat(payout.path("transactionDescription").asString()).isEqualTo("얼리버드");
            assertThat(payout.has("idempotencyKey")).isFalse();
        } catch (Exception exception) {
            throw new AssertionError("토스 지급대행 요청 JWE를 검증하지 못했습니다.", exception);
        }
    }

    private static String encryptedResponse(String plainText) throws Exception {
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).build(),
                new Payload(plainText)
        );
        jweObject.encrypt(new DirectEncrypter(SECURITY_KEY));
        return jweObject.serialize();
    }

    private static String successResponse(String status, String errorCode) {
        String error = errorCode == null
                ? "null"
                : "{\"code\":\"" + errorCode + "\",\"message\":\"실패\"}";
        return """
                {
                  "version": "2022-11-16",
                  "traceId": "trace-1",
                  "entityType": "payout-list",
                  "entityBody": {
                    "hasMore": false,
                    "size": 1,
                    "nextCursor": null,
                    "items": [
                      {
                        "id": "toss-payout-1",
                        "refPayoutId": "ref-payout-1",
                        "destination": "seller-1",
                        "scheduleType": "SCHEDULED",
                        "payoutDate": "2026-08-03",
                        "amount": {"currency": "KRW", "value": 10000},
                        "transactionDescription": "얼리버드",
                        "status": "%s",
                        "error": %s
                      }
                    ]
                  }
                }
                """.formatted(status, error);
    }

    private static ScheduledPayoutRequest request() {
        return new ScheduledPayoutRequest(
                "ref-payout-1",
                "seller-1",
                LocalDate.of(2026, 8, 3),
                Money.wons(10_000),
                "얼리버드",
                "idempotency-1"
        );
    }
}
