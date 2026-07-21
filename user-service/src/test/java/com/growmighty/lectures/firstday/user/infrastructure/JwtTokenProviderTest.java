package com.growmighty.lectures.firstday.user.infrastructure;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.common.jwt.JwtProperties;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "test-signing-key-at-least-256-bits-long!".getBytes());
    private static final long EXPIRATION_SECONDS = 3600L;

    private final SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(TEST_SECRET), "HmacSHA256");
    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();

    private final JwtTokenProvider tokenProvider =
            new JwtTokenProvider(encoder, new JwtProperties(TEST_SECRET, EXPIRATION_SECONDS));

    @Test
    @DisplayName("발급한 토큰의 subject 는 userId, role 클레임은 요청한 role 이다")
    void issueAccessToken_encodesUserIdAndRole() {
        String token = tokenProvider.issueAccessToken(1L, UserRole.BACKER);

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getClaimAsString(JwtHeaders.ROLE_CLAIM)).isEqualTo(UserRole.BACKER.getRoleName());
    }

    @Test
    @DisplayName("role 별로 클레임 값이 달라진다")
    void issueAccessToken_differsByRole() {
        Jwt creatorToken = decoder.decode(tokenProvider.issueAccessToken(2L, UserRole.CREATOR));
        Jwt adminToken = decoder.decode(tokenProvider.issueAccessToken(3L, UserRole.ADMIN));

        assertThat(creatorToken.getClaimAsString(JwtHeaders.ROLE_CLAIM)).isEqualTo(UserRole.CREATOR.getRoleName());
        assertThat(adminToken.getClaimAsString(JwtHeaders.ROLE_CLAIM)).isEqualTo(UserRole.ADMIN.getRoleName());
    }

    @Test
    @DisplayName("만료 시각은 발급 시각으로부터 설정된 만료 시간만큼 뒤다")
    void issueAccessToken_expiresAfterConfiguredDuration() {
        Jwt jwt = decoder.decode(tokenProvider.issueAccessToken(1L, UserRole.BACKER));

        Duration lifetime = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());

        assertThat(lifetime).isCloseTo(Duration.ofSeconds(EXPIRATION_SECONDS), Duration.ofSeconds(1));
    }
}
