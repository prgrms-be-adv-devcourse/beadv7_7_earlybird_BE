package com.growmighty.lectures.firstday.systemtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이슈 #149 — 게이트웨이를 통해 실제 후원자 플로우(회원가입 → 로그인 → 프로젝트/리워드 조회 →
 * 후원 생성 → 내 후원 조회 → 취소)를 실행해 검증하는 라이브 테스트. user/project/order-service
 * 어느 한 곳 소유가 아니라 여러 서비스를 가로지르므로 system-test 모듈에 둔다.
 *
 * 기본 `test` 태스크에서는 제외된다(@Tag("live") + build.gradle의 excludeTags) — 로컬/운영
 * 스택이 이미 떠 있어야 하기 때문이다. 실행: ./gradlew :system-test:liveTest
 * -Dsystem-test.baseUrl=... 로 로컬/운영 baseUrl 을 바꿔가며 돌린다(기본값 로컬 게이트웨이).
 */
@Tag("live")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackerFlowLiveTest {

    private static final String BASE_URL = System.getProperty("system-test.baseUrl", "http://localhost:8000");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMAIL = "backerflow." + System.currentTimeMillis() + "@earlybird.co.kr";

    private static String accessToken;
    private static Long userId;
    private static Long projectId;
    private static Long rewardId;
    private static BigDecimal rewardPrice;
    private static Long orderId;

    @Test
    @Order(1)
    void signup() throws Exception {
        String body = """
                {"email":"%s","password":"rawPassword1!","name":"플로우테스트","phoneNumber":"010-1234-5678"}
                """.formatted(EMAIL);
        JsonNode data = post("/api/v1/users/signup", body, null, 200);
        userId = data.get("id").asLong();
    }

    @Test
    @Order(2)
    void login() throws Exception {
        String body = """
                {"email":"%s","password":"rawPassword1!"}
                """.formatted(EMAIL);
        JsonNode data = post("/api/v1/users/login", body, null, 200);
        accessToken = data.get("accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(3)
    void browseProjectsAndRewards() throws Exception {
        JsonNode projects = get("/api/v1/projects?status=IN_PROGRESS", accessToken, 200);
        assertThat(projects.isArray()).isTrue();
        assertThat(projects.size()).isGreaterThan(0);
        projectId = projects.get(0).get("projectId").asLong();

        JsonNode rewards = get("/api/v1/projects/" + projectId + "/rewards", accessToken, 200);
        assertThat(rewards.size()).isGreaterThan(0);
        rewardId = rewards.get(0).get("rewardId").asLong();
        rewardPrice = new BigDecimal(rewards.get(0).get("price").asText());
    }

    @Test
    @Order(4)
    void placeOrder() throws Exception {
        BigDecimal itemsAmount = rewardPrice;
        BigDecimal shippingFee = itemsAmount.compareTo(BigDecimal.valueOf(50_000)) >= 0
                ? BigDecimal.ZERO : BigDecimal.valueOf(3_000);
        BigDecimal totalAmount = itemsAmount.add(shippingFee);

        String body = """
                {
                  "userId": %d,
                  "requests": [{"rewardId": %d, "quantity": 1, "expectedUnitPrice": %s}],
                  "receiverName": "플로우테스트",
                  "receiverPhone": "010-1234-5678",
                  "shippingAddress": "서울시 강남구",
                  "zipCode": "06236",
                  "expectedItemsAmount": %s,
                  "expectedTotalAmount": %s
                }
                """.formatted(userId, rewardId, rewardPrice.toPlainString(),
                itemsAmount.toPlainString(), totalAmount.toPlainString());

        JsonNode data = post("/api/v1/orders", body, accessToken, 200);
        orderId = data.get("orderId") != null ? data.get("orderId").asLong() : data.get("id").asLong();
        assertThat(data.get("status").asText()).isEqualTo("PAID");
    }

    @Test
    @Order(5)
    void getMyOrders() throws Exception {
        JsonNode orders = get("/api/v1/orders/me?userId=" + userId, accessToken, 200);
        assertThat(orders.isArray()).isTrue();
        assertThat(orders.size()).isGreaterThan(0);
    }

    @Test
    @Order(6)
    void cancelOrder() throws Exception {
        JsonNode data = post("/api/v1/orders/" + orderId + "/cancel", "", accessToken, 200);
        assertThat(data.get("status").asText()).isEqualTo("CANCELLED");
    }

    private JsonNode post(String path, String body, String token, int expectedStatus) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .withFailMessage("POST %s failed: %d %s", path, response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        return MAPPER.readTree(response.body()).get("data");
    }

    private JsonNode get(String path, String token, int expectedStatus) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .withFailMessage("GET %s failed: %d %s", path, response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        return MAPPER.readTree(response.body()).get("data");
    }
}
