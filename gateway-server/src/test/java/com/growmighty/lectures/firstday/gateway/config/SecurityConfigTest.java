package com.growmighty.lectures.firstday.gateway.config;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
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
        webTestClient.get().uri("/users/me")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("유효한 JWT 로 보호된 경로를 호출하면 401 이 아니다 (보안 계층은 통과)")
    void protectedPath_withValidToken_passesSecurityLayer() {
        String token = issueToken(Instant.now(), Instant.now().plusSeconds(3600));

        webTestClient.get().uri("/users/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        Assertions.assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    @DisplayName("만료된 JWT 로 보호된 경로를 호출하면 401")
    void protectedPath_withExpiredToken_isUnauthorized() {
        String token = issueToken(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        webTestClient.get().uri("/users/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("로그인/회원가입은 Authorization 헤더 없이도 보안 계층에서 거부되지 않는다")
    void publicPaths_withoutToken_areNotRejectedBySecurityLayer() {
        webTestClient.post().uri("/users/login")
                .exchange()
                .expectStatus().value(status ->
                        Assertions.assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 /admin/** 를 호출하면 401")
    void adminPath_withoutToken_isUnauthorized() {
        webTestClient.get().uri("/admin/projects")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ADMIN 이 아닌 role(BACKER) 의 JWT 로 /admin/** 를 호출하면 403")
    void adminPath_withNonAdminRole_isForbidden() {
        String token = issueToken(Instant.now(), Instant.now().plusSeconds(3600), UserRole.BACKER.getRoleName());

        webTestClient.get().uri("/admin/projects")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ADMIN role 의 JWT 로 /admin/** 를 호출하면 보안 계층은 통과한다 (401/403 아님)")
    void adminPath_withAdminRole_passesSecurityLayer() {
        String token = issueToken(Instant.now(), Instant.now().plusSeconds(3600), UserRole.ADMIN.getRoleName());

        webTestClient.get().uri("/admin/projects")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status -> Assertions.assertThat(status)
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
    }

    private String issueToken(Instant issuedAt, Instant expiresAt) {
        return issueToken(issuedAt, expiresAt, UserRole.BACKER.getRoleName());
    }

    private String issueToken(Instant issuedAt, Instant expiresAt, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .claim("role", role)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
