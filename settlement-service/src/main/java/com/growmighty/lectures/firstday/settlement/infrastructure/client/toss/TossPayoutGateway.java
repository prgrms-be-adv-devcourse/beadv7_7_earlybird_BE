package com.growmighty.lectures.firstday.settlement.infrastructure.client.toss;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class TossPayoutGateway implements PayoutGateway {

    static final String SECURITY_MODE_HEADER = "TossPayments-api-security-mode";
    static final String SECURITY_MODE_ENCRYPTION = "ENCRYPTION";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final String PAYOUTS_PATH = "/v2/payouts";
    private static final String SCHEDULED = "SCHEDULED";
    private static final String KRW = "KRW";

    private final RestClient restClient;
    private final TossPayoutJweCodec jweCodec;
    private final ObjectMapper objectMapper;
    private final String secretKey;

    public TossPayoutGateway(
            RestClient restClient,
            TossPayoutJweCodec jweCodec,
            ObjectMapper objectMapper,
            String secretKey
    ) {
        this.restClient = Objects.requireNonNull(restClient, "토스 지급대행 HTTP 클라이언트는 필수입니다.");
        this.jweCodec = Objects.requireNonNull(jweCodec, "토스 지급대행 JWE 모듈은 필수입니다.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 매퍼는 필수입니다.");
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("토스 지급대행 시크릿 키는 필수입니다.");
        }
        this.secretKey = secretKey;
    }

    @Override
    public PayoutGatewayResult requestScheduledPayout(ScheduledPayoutRequest request) {
        Objects.requireNonNull(request, "예약 지급 요청은 필수입니다.");

        try {
            String requestBody = serializeRequest(request);
            String encryptedBody = jweCodec.encrypt(requestBody);
            TossHttpResponse response = requestPayout(encryptedBody, request.idempotencyKey());
            return decodeResponse(response);
        } catch (PayoutGatewayException exception) {
            throw exception;
        } catch (RestClientException | TossPayoutSecurityException exception) {
            throw new PayoutGatewayException("토스 지급대행 요청 결과를 확인하지 못했습니다.", exception);
        }
    }

    private String serializeRequest(ScheduledPayoutRequest request) {
        TossScheduledPayoutRequest tossRequest = new TossScheduledPayoutRequest(
                request.refPayoutId(),
                request.sellerId(),
                SCHEDULED,
                request.payoutDate().toString(),
                new TossAmount(KRW, request.amount().amount()),
                request.transactionDescription()
        );
        try {
            return objectMapper.writeValueAsString(List.of(tossRequest));
        } catch (JacksonException exception) {
            throw new PayoutGatewayException("토스 지급대행 요청 본문을 생성하지 못했습니다.", exception);
        }
    }

    private TossHttpResponse requestPayout(String encryptedBody, String idempotencyKey) {
        return restClient.post()
                .uri(PAYOUTS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.setBasicAuth(secretKey, "", StandardCharsets.UTF_8);
                    headers.set(SECURITY_MODE_HEADER, SECURITY_MODE_ENCRYPTION);
                    headers.set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
                })
                .body(encryptedBody)
                .exchangeForRequiredValue((request, response) -> new TossHttpResponse(
                        response.getStatusCode(),
                        response.bodyTo(String.class)
                ));
    }

    private PayoutGatewayResult decodeResponse(TossHttpResponse response) {
        if (response.body() == null || response.body().isBlank()) {
            throw new PayoutGatewayException("토스 지급대행 응답 본문이 비어 있습니다.");
        }

        String decryptedBody = jweCodec.decrypt(response.body());
        JsonNode root;
        try {
            root = objectMapper.readTree(decryptedBody);
        } catch (JacksonException exception) {
            throw new PayoutGatewayException("토스 지급대행 응답 JSON을 해석하지 못했습니다.", exception);
        }

        if (response.statusCode().is2xxSuccessful()) {
            return acceptedResult(root);
        }
        return rejectedResult(root);
    }

    private PayoutGatewayResult.Accepted acceptedResult(JsonNode root) {
        JsonNode items = root.path("entityBody").path("items");
        if (!items.isArray() || items.size() != 1) {
            throw new PayoutGatewayException("토스 지급대행 응답에 단일 지급 결과가 없습니다.");
        }

        JsonNode payout = items.get(0);
        String payoutId = requiredText(payout, "id");
        PayoutAttemptStatus status = mapStatus(requiredText(payout, "status"));
        String errorCode = optionalText(payout.path("error"), "code");
        return new PayoutGatewayResult.Accepted(payoutId, status, errorCode);
    }

    private PayoutGatewayResult.Rejected rejectedResult(JsonNode root) {
        String errorCode = optionalText(root.path("error"), "code");
        if (errorCode == null) {
            errorCode = optionalText(root, "code");
        }
        if (errorCode == null) {
            throw new PayoutGatewayException("토스 지급대행 실패 응답에 오류 코드가 없습니다.");
        }
        return new PayoutGatewayResult.Rejected(errorCode);
    }

    private static PayoutAttemptStatus mapStatus(String tossStatus) {
        return switch (tossStatus) {
            case "REQUESTED" -> PayoutAttemptStatus.REQUESTED;
            case "IN_PROGRESS" -> PayoutAttemptStatus.IN_PROGRESS;
            case "COMPLETED" -> PayoutAttemptStatus.COMPLETED;
            case "FAILED", "REJECTED" -> PayoutAttemptStatus.FAILED;
            case "CANCELED", "DELETED" -> PayoutAttemptStatus.CANCELED;
            default -> throw new PayoutGatewayException(
                    "지원하지 않는 토스 지급대행 상태입니다: " + tossStatus
            );
        };
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (value == null) {
            throw new PayoutGatewayException(
                    "토스 지급대행 응답에 필수 필드가 없습니다: " + fieldName
            );
        }
        return value;
    }

    private static String optionalText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (!value.isString() || value.asString().isBlank()) {
            return null;
        }
        return value.asString();
    }

    private record TossScheduledPayoutRequest(
            String refPayoutId,
            String destination,
            String scheduleType,
            String payoutDate,
            TossAmount amount,
            String transactionDescription
    ) {
    }

    private record TossAmount(String currency, java.math.BigDecimal value) {
    }

    private record TossHttpResponse(HttpStatusCode statusCode, String body) {
    }
}
