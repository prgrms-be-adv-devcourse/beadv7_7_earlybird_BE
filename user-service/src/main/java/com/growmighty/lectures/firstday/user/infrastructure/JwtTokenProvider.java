package com.growmighty.lectures.firstday.user.infrastructure;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.common.jwt.JwtProperties;
import com.growmighty.lectures.firstday.user.application.TokenProvider;
import com.growmighty.lectures.firstday.user.domain.UserRole;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JwtTokenProvider implements TokenProvider {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public String issueAccessToken(Long userId, UserRole role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim(JwtHeaders.ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.accessTokenExpirationSeconds()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
