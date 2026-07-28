package com.growmighty.lectures.firstday.settlement.presentation;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

final class TestJwtTokens {

    private static final String TEST_JWT_SECRET = "A196b/7T15tWsckvVi3uwbzkfgbxZnvVYHTQ5kl+6nQ=";
    private static final JwtEncoder JWT_ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec(Base64.getDecoder().decode(TEST_JWT_SECRET), "HmacSHA256")
    ));

    private TestJwtTokens() {
    }

    static String adminBearerToken() {
        return bearerToken("ADMIN");
    }

    static String bearerToken(String role) {
        Instant now = Instant.now();
        return bearerToken(role, now, now.plusSeconds(3600));
    }

    static String expiredBearerToken(String role) {
        Instant now = Instant.now();
        return bearerToken(role, now.minusSeconds(7200), now.minusSeconds(3600));
    }

    private static String bearerToken(String role, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .claim("role", role)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = JWT_ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return "Bearer " + token;
    }
}
