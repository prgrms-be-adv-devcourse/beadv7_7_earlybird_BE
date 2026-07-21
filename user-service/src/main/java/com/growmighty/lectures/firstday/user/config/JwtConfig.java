package com.growmighty.lectures.firstday.user.config;

import com.growmighty.lectures.firstday.common.jwt.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKeySpec key = createJwtSecretKey(properties);
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /** 리프레시 토큰 검증용. gateway 는 access token 만 검증하므로, refresh token 검증은 발급처인 user-service 가 직접 한다. */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKeySpec key = createJwtSecretKey(properties);
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    private static @NonNull SecretKeySpec createJwtSecretKey(@NonNull JwtProperties properties) {
        return new SecretKeySpec(Base64.getDecoder().decode(properties.secret()), "HmacSHA256");
    }
}
