package com.growmighty.lectures.firstday.gateway.config;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.common.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static com.growmighty.lectures.firstday.common.entity.UserRole.*;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    public static final String URI_PREFIX_API = "/api/v1";
    public static final String URI_PREFIX_INTERNAL = "/internal/v1";

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // 인증 불필요
                        .pathMatchers(HttpMethod.POST,
                                URI_PREFIX_API + "/users/signup",
                                URI_PREFIX_API + "/users/login",
                                URI_PREFIX_API + "/users/refresh").permitAll()
                        .pathMatchers(HttpMethod.GET,
                                "/*/v3/api-docs", "/*/v3/api-docs/**",
                                "/*/swagger-ui.html", "/*/swagger-ui/**").permitAll()
                        
                        // payments
                        //// PG사가 직접 호출
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/payments/webhook").permitAll()

                        // projects
                        .pathMatchers(HttpMethod.GET, URI_PREFIX_API + "/projects/me").hasRole(CREATOR.getCode())
                        .pathMatchers(HttpMethod.GET,
                                URI_PREFIX_API + "/projects",
                                URI_PREFIX_API + "/projects/*",
                                URI_PREFIX_API + "/projects/*/rewards").permitAll()
                        .pathMatchers(HttpMethod.GET, URI_PREFIX_API + "/projects/*/orders").hasRole(CREATOR.getCode())
                        //// 창작자 전용
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/projects").hasAnyRole(CREATOR.getCode(), ADMIN.getCode())
                        .pathMatchers(HttpMethod.PATCH, URI_PREFIX_API + "/projects/*").hasRole(CREATOR.getCode())
                        .pathMatchers(HttpMethod.DELETE, URI_PREFIX_API + "/projects/*").hasRole(CREATOR.getCode())
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/projects/*/notices").hasRole(CREATOR.getCode())
                        //// 관리자 전용
                        .pathMatchers(HttpMethod.POST,
                                URI_PREFIX_API + "/projects/*/approve",
                                URI_PREFIX_API + "/projects/*/reject",
                                URI_PREFIX_API + "/projects/close-expired",
                                URI_PREFIX_API + "/projects/*/close-early").hasRole(ADMIN.getCode())
                        .pathMatchers(HttpMethod.PATCH, URI_PREFIX_API + "/projects/*/deadline").hasRole(ADMIN.getCode())
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/projects/*/cancel").hasAnyRole(CREATOR.getCode(), ADMIN.getCode())

                        // project-categories
                        .pathMatchers(HttpMethod.GET,
                                URI_PREFIX_API + "/project-categories",
                                URI_PREFIX_API + "/project-categories/*").permitAll()
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/project-categories").hasRole(ADMIN.getCode())
                        .pathMatchers(HttpMethod.PUT, URI_PREFIX_API + "/project-categories/*").hasRole(ADMIN.getCode())

                        // rewards
                        .pathMatchers(HttpMethod.GET, URI_PREFIX_API + "/rewards/*").permitAll()
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/projects/*/rewards").hasRole(CREATOR.getCode())
                        .pathMatchers(HttpMethod.PATCH, URI_PREFIX_API + "/rewards/*").hasRole(CREATOR.getCode())
                        .pathMatchers(HttpMethod.DELETE, URI_PREFIX_API + "/rewards/*").hasRole(CREATOR.getCode())
                        //// 관리자 전용
                        .pathMatchers(HttpMethod.PATCH, URI_PREFIX_API + "/rewards/*/quantity").hasRole(ADMIN.getCode())
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/rewards/*/deactivate").hasRole(ADMIN.getCode())

                        // notices
                        .pathMatchers(HttpMethod.PATCH, URI_PREFIX_API + "/notices/*").hasRole(CREATOR.getCode())
                        .pathMatchers(HttpMethod.DELETE, URI_PREFIX_API + "/notices/*").hasAnyRole(CREATOR.getCode(), ADMIN.getCode())

                        // settlements
                        .pathMatchers(HttpMethod.GET, URI_PREFIX_API + "/settlements/all").hasRole(ADMIN.getCode())
                        .pathMatchers(HttpMethod.GET,
                                URI_PREFIX_API + "/settlements",
                                URI_PREFIX_API + "/settlements/*").hasRole(CREATOR.getCode())
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/settlements/close").hasRole(ADMIN.getCode())

                        // reports
                        .pathMatchers(HttpMethod.GET, URI_PREFIX_API + "/reports").hasRole(ADMIN.getCode())
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/reports/*/process").hasRole(ADMIN.getCode())

                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtDecoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(properties.secret()), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }

    /** JWT 의 role 클레임(예: ADMIN)을 Spring Security 의 ROLE_ADMIN 권한으로 변환한다 — hasRole() 이 이 형식을 기대한다. */
    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(JwtHeaders.ROLE_CLAIM);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
