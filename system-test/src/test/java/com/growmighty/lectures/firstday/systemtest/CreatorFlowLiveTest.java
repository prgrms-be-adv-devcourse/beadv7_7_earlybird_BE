package com.growmighty.lectures.firstday.systemtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게이트웨이를 통해 실제 창작자 플로우(회원가입 → 로그인 → 창작자 전환 → 토큰 재발급 →
 * 프로젝트 생성 → 리워드 등록 → 내 프로젝트/리워드 조회 → 관리자 심사 승인)를 실행해 검증하는
 * 라이브 테스트. 마지막 두 단계(관리자 로그인/승인)는 UserDataInitializer가 시드하는
 * admin@earlybird.co.kr 계정을 쓴다 — 별도 승격 API가 없어 시드 데이터로만 존재한다.
 *
 * 기본 `test` 태스크에서는 제외된다(@Tag("live") + build.gradle의 excludeTags). 실행:
 * ./gradlew :system-test:liveTest -Dsystem-test.baseUrl=... (기본값 로컬 게이트웨이)
 *
 * 주의: 루트의 creator-flow.http 가 같은 시나리오를 의도적으로 중복 구현한다 — 그쪽은 수동
 * 확인/시연용, 이쪽은 자동화(이슈 #157)용. 이 테스트를 고치면 creator-flow.http 도 같이 고칠 것.
 */
@Tag("live")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CreatorFlowLiveTest {

    private static final String BASE_URL = System.getProperty("system-test.baseUrl", "http://localhost:8000");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMAIL = "creatorflow." + System.currentTimeMillis() + "@earlybird.co.kr";

    private static String refreshToken;
    private static String accessToken;
    private static Long categoryId;
    private static Long projectId;
    private static Long rewardId;
    private static String adminAccessToken;

    @Test
    @Order(1)
    void signup() throws Exception {
        String body = """
                {"email":"%s","password":"rawPassword1!","name":"창작자플로우","phoneNumber":"010-9876-5432"}
                """.formatted(EMAIL);
        post("/api/v1/users/signup", body, null, 200);
    }

    @Test
    @Order(2)
    void login() throws Exception {
        String body = """
                {"email":"%s","password":"rawPassword1!"}
                """.formatted(EMAIL);
        JsonNode data = post("/api/v1/users/login", body, null, 200);
        accessToken = data.get("accessToken").asText();
        refreshToken = data.get("refreshToken").asText();
        assertThat(data.get("user").get("role").asText()).isEqualTo("BACKER");
    }

    @Test
    @Order(3)
    void registerAsCreator() throws Exception {
        String body = """
                {"bankName":"신한은행","accountNumber":"110-123-456789","accountHolder":"창작자플로우"}
                """;
        JsonNode data = post("/api/v1/users/me/creator", body, accessToken, 200);
        assertThat(data.get("role").asText()).isEqualTo("CREATOR");
    }

    @Test
    @Order(4)
    void refreshTokenPicksUpCreatorRole() throws Exception {
        // 로그인 때 발급된 access token 은 아직 BACKER role 을 담고 있다 — 창작자 전환 후 최신
        // role 을 반영하려면 refresh token 으로 access token 을 재발급받아야 한다(docs/3_JWT_AUTH.md).
        String body = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
        JsonNode data = post("/api/v1/users/refresh", body, null, 200);
        accessToken = data.get("accessToken").asText();
    }

    @Test
    @Order(5)
    void pickCategory() throws Exception {
        JsonNode categories = get("/api/v1/project-categories", accessToken, 200);
        assertThat(categories.size()).isGreaterThan(0);
        categoryId = categories.get(0).get("id").asLong();
    }

    @Test
    @Order(6)
    void createProject() throws Exception {
        String body = """
                {
                  "title": "창작자 플로우 테스트 프로젝트",
                  "categoryId": %d,
                  "summary": "라이브 테스트로 생성된 프로젝트",
                  "description": "creator-flow.http / CreatorFlowLiveTest 검증용",
                  "goalAmount": 1000000,
                  "startAt": "%s",
                  "endAt": "%s"
                }
                """.formatted(categoryId, LocalDateTime.now(), LocalDate.now().plusDays(30));
        JsonNode data = post("/api/v1/projects", body, accessToken, 200);
        projectId = data.get("projectId").asLong();
        assertThat(data.get("status").asText()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @Order(7)
    void registerReward() throws Exception {
        String body = """
                {"name":"얼리버드 리워드","description":"라이브 테스트용 리워드","price":10000,"totalQuantity":50}
                """;
        JsonNode data = post("/api/v1/projects/" + projectId + "/rewards", body, accessToken, 200);
        rewardId = data.get("rewardId").asLong();
    }

    @Test
    @Order(8)
    void getMyProjects() throws Exception {
        JsonNode projects = get("/api/v1/projects/me", accessToken, 200);
        boolean found = false;
        for (JsonNode p : projects) {
            if (p.get("projectId").asLong() == projectId) {
                found = true;
                break;
            }
        }
        assertThat(found).withFailMessage("created project %s not found in /projects/me", projectId).isTrue();
    }

    @Test
    @Order(9)
    void getProjectRewards() throws Exception {
        JsonNode rewards = get("/api/v1/projects/" + projectId + "/rewards", accessToken, 200);
        boolean found = false;
        for (JsonNode r : rewards) {
            if (r.get("rewardId").asLong() == rewardId) {
                found = true;
                break;
            }
        }
        assertThat(found).withFailMessage("created reward %s not found in project rewards", rewardId).isTrue();
    }

    @Test
    @Order(10)
    void adminLogin() throws Exception {
        // UserDataInitializer가 시드하는 계정 — 승격 API가 없어(§운영 절차 전용) 시드 데이터로만 존재한다.
        String body = """
                {"email":"admin@earlybird.co.kr","password":"rawPassword3!"}
                """;
        JsonNode data = post("/api/v1/users/login", body, null, 200);
        assertThat(data.get("user").get("role").asText()).isEqualTo("ADMIN");
        adminAccessToken = data.get("accessToken").asText();
    }

    @Test
    @Order(11)
    void adminApprovesProject() throws Exception {
        JsonNode data = post("/api/v1/projects/" + projectId + "/approve", "", adminAccessToken, 200);
        assertThat(data.get("status").asText()).isEqualTo("IN_PROGRESS");
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
