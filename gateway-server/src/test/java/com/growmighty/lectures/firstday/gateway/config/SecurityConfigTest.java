package com.growmighty.lectures.firstday.gateway.config;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

/**
 * SecurityMockServerConfigurers.mockJwt() 는 mock 바인딩(bindToApplicationContext 등) 클라이언트에서만
 * 동작한다 — RANDOM_PORT 로 실제 서버에 붙는 WebTestClient 에는 적용할 수 없다
 * ("Cannot apply Spring Security Test Support to null WebHttpHandlerBuilder").
 * 그래서 여기서는 테스트 시크릿으로 실제 JWT 를 직접 발급해 Authorization 헤더로 보낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Value("${jwt.secret}")
    private static final String TEST_SECRET = "A196b/7T15tWsckvVi3uwbzkfgbxZnvVYHTQ5kl+6nQ=";

    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec(Base64.getDecoder().decode(TEST_SECRET), "HmacSHA256")));

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Authorization 헤더 없이 보호된 경로를 호출하면 401")
    void protectedPath_withoutToken_isUnauthorized() {
        webTestClient.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("유효한 JWT 로 보호된 경로를 호출하면 401 이 아니다 (보안 계층은 통과)")
    void protectedPath_withValidToken_passesSecurityLayer() {
        String token = issueToken(Instant.now(), Instant.now().plusSeconds(3600));

        webTestClient.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        Assertions.assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    @DisplayName("만료된 JWT 로 보호된 경로를 호출하면 401")
    void protectedPath_withExpiredToken_isUnauthorized() {
        String token = issueToken(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        webTestClient.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("로그인/회원가입은 Authorization 헤더 없이도 보안 계층에서 거부되지 않는다")
    void publicPaths_withoutToken_areNotRejectedBySecurityLayer() {
        webTestClient.post().uri("/api/v1/users/login")
                .exchange()
                .expectStatus().value(status ->
                        Assertions.assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    @DisplayName("프로젝트/카테고리/리워드 조회 경로는 Authorization 헤더 없이도 보안 계층에서 거부되지 않는다")
    void projectReadPaths_withoutToken_areNotRejectedBySecurityLayer() {
        for (String uri : new String[] {
                "/api/v1/projects",
                "/api/v1/projects/1",
                "/api/v1/projects/1/rewards",
                "/api/v1/project-categories",
                "/api/v1/project-categories/1",
                "/api/v1/rewards/1"
        }) {
            webTestClient.get().uri(uri)
                    .exchange()
                    .expectStatus().value(status ->
                            Assertions.assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
        }
    }

    @Test
    @DisplayName("PG 웹훅 경로는 Authorization 헤더 없이도 보안 계층에서 거부되지 않는다")
    void paymentsWebhook_withoutToken_isNotRejectedBySecurityLayer() {
        webTestClient.post().uri("/api/v1/payments/webhook")
                .exchange()
                .expectStatus().value(status ->
                        Assertions.assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    @DisplayName("창작자(CREATOR) 전용 라우트는 BACKER 토큰으로 호출하면 403")
    void creatorOnlyPath_withBackerToken_isForbidden() {
        String token = issueToken(UserRole.BACKER);

        webTestClient.post().uri("/api/v1/projects")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("창작자(CREATOR) 전용 라우트는 CREATOR 토큰으로 호출하면 403/401이 아니다")
    void creatorOnlyPath_withCreatorToken_passesSecurityLayer() {
        String token = issueToken(UserRole.CREATOR);

        webTestClient.post().uri("/api/v1/projects")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> Assertions.assertThat(status)
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
    }

    @Test
    @DisplayName("/projects/me 는 /projects/* 와일드카드가 아니라 CREATOR 전용 규칙에 매칭된다 (BACKER면 403)")
    void projectsMe_withBackerToken_isForbidden() {
        String token = issueToken(UserRole.BACKER);

        webTestClient.get().uri("/api/v1/projects/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("/settlements/all 은 /settlements/* 와일드카드가 아니라 ADMIN 전용 규칙에 매칭된다 (CREATOR면 403)")
    void settlementsAll_withCreatorToken_isForbidden() {
        String token = issueToken(UserRole.CREATOR);

        webTestClient.get().uri("/api/v1/settlements/all")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("/settlements/all 은 ADMIN 토큰이면 403/401이 아니다")
    void settlementsAll_withAdminToken_passesSecurityLayer() {
        String token = issueToken(UserRole.ADMIN);

        webTestClient.get().uri("/api/v1/settlements/all")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> Assertions.assertThat(status)
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
    }

    @Test
    @DisplayName("관리자(ADMIN) 전용 라우트는 CREATOR 토큰으로 호출하면 403")
    void adminOnlyPath_withCreatorToken_isForbidden() {
        String token = issueToken(UserRole.CREATOR);

        webTestClient.post().uri("/api/v1/projects/1/approve")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("관리자(ADMIN) 전용 라우트는 ADMIN 토큰이면 403/401이 아니다")
    void adminOnlyPath_withAdminToken_passesSecurityLayer() {
        String token = issueToken(UserRole.ADMIN);

        webTestClient.post().uri("/api/v1/projects/1/approve")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> Assertions.assertThat(status)
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
    }

    @Test
    @DisplayName("신고 목록 조회는 관리자 전용 — BACKER 토큰이면 403")
    void reportsList_withBackerToken_isForbidden() {
        String token = issueToken(UserRole.BACKER);

        webTestClient.get().uri("/api/v1/reports")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("공지 삭제는 CREATOR/ADMIN 둘 다 허용되지만 BACKER는 403")
    void noticeDelete_allowsCreatorAndAdmin_rejectsBacker() {
        webTestClient.delete().uri("/api/v1/notices/1")
                .header("Authorization", "Bearer " + issueToken(UserRole.BACKER))
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.delete().uri("/api/v1/notices/1")
                .header("Authorization", "Bearer " + issueToken(UserRole.CREATOR))
                .exchange()
                .expectStatus().value(status -> Assertions.assertThat(status)
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));

        webTestClient.delete().uri("/api/v1/notices/1")
                .header("Authorization", "Bearer " + issueToken(UserRole.ADMIN))
                .exchange()
                .expectStatus().value(status -> Assertions.assertThat(status)
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
    }

    /**
     * SecurityConfig 의 역할 제한 라우트 전체({@code hasRole}/{@code hasAnyRole})를 표로 관리한다 —
     * 위에서 대표 라우트 몇 개만 이름 붙여 검증한 것과 별개로, 새 라우트가 추가되면서 보안 설정을
     * 깜빡 빠뜨리는 회귀를 잡기 위한 전수 검사다.
     */
    private static Stream<Arguments> roleGatedRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/projects/me", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.GET, "/api/v1/projects/1/orders", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.POST, "/api/v1/projects", List.of(UserRole.CREATOR, UserRole.ADMIN)),
                Arguments.of(HttpMethod.PATCH, "/api/v1/projects/1", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.DELETE, "/api/v1/projects/1", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.POST, "/api/v1/projects/1/notices", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.POST, "/api/v1/projects/1/approve", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/projects/1/reject", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/projects/close-expired", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/projects/1/close-early", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.PATCH, "/api/v1/projects/1/deadline", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/projects/1/cancel", List.of(UserRole.CREATOR, UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/project-categories", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.PUT, "/api/v1/project-categories/1", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.DELETE, "/api/v1/project-categories/1", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/projects/1/rewards", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.PATCH, "/api/v1/rewards/1", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.DELETE, "/api/v1/rewards/1", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.PATCH, "/api/v1/rewards/1/quantity", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/rewards/1/deactivate", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.PATCH, "/api/v1/notices/1", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.DELETE, "/api/v1/notices/1", List.of(UserRole.CREATOR, UserRole.ADMIN)),
                Arguments.of(HttpMethod.GET, "/api/v1/settlements", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.GET, "/api/v1/settlements/1", List.of(UserRole.CREATOR)),
                Arguments.of(HttpMethod.GET, "/api/v1/settlements/all", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/settlements/close", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.GET, "/api/v1/reports", List.of(UserRole.ADMIN)),
                Arguments.of(HttpMethod.POST, "/api/v1/reports/1/process", List.of(UserRole.ADMIN))
        );
    }

    @ParameterizedTest(name = "{0} {1} 은 허용된 role({2}) 토큰이면 401/403 이 아니다")
    @MethodSource("roleGatedRoutes")
    @DisplayName("역할 제한 라우트 전수 검사 — 허용된 role")
    void roleGatedRoute_withAllowedRole_passesSecurityLayer(HttpMethod method, String uri, List<UserRole> allowedRoles) {
        for (UserRole role : allowedRoles) {
            webTestClient.method(method).uri(uri)
                    .header("Authorization", "Bearer " + issueToken(role))
                    .exchange()
                    .expectStatus().value(status -> Assertions.assertThat(status)
                            .as("%s %s 는 %s 토큰으로 401/403 이 아니어야 한다", method, uri, role)
                            .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
        }
    }

    @ParameterizedTest(name = "{0} {1} 은 BACKER 토큰이면 403")
    @MethodSource("roleGatedRoutes")
    @DisplayName("역할 제한 라우트 전수 검사 — BACKER 는 차단")
    void roleGatedRoute_withBackerToken_isForbidden(HttpMethod method, String uri, List<UserRole> allowedRoles) {
        webTestClient.method(method).uri(uri)
                .header("Authorization", "Bearer " + issueToken(UserRole.BACKER))
                .exchange()
                .expectStatus().isForbidden();
    }

    private String issueToken(Instant issuedAt, Instant expiresAt) {
        return issueToken(issuedAt, expiresAt, UserRole.BACKER);
    }

    private String issueToken(UserRole role) {
        return issueToken(Instant.now(), Instant.now().plusSeconds(3600), role);
    }

    private String issueToken(Instant issuedAt, Instant expiresAt, UserRole role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .claim("role", role.getCode())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
