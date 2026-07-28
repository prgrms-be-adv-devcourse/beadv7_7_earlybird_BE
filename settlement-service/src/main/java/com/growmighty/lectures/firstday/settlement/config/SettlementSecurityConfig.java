package com.growmighty.lectures.firstday.settlement.config;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(SettlementJwtProperties.class)
public class SettlementSecurityConfig {

    private static final String MANUAL_SETTLEMENT_RUN = "/internal/v1/settlements/runs";
    private static final String ADMIN_SETTLEMENTS_PATH = "/api/v1/settlements/all";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String CREATOR_ROLE = "CREATOR";
    private static final String AUTHENTICATION_REQUIRED = "유효한 관리자 인증이 필요합니다.";
    private static final String ADMIN_AUTHORITY_REQUIRED = "관리자 권한이 필요합니다.";
    private static final String CREATOR_AUTHORITY_REQUIRED = "창작자 권한이 필요합니다.";

    @Bean
    public SettlementSecurityErrorResponder settlementSecurityErrorResponder(ObjectMapper objectMapper) {
        return new SettlementSecurityErrorResponder(objectMapper);
    }

    @Bean
    public SettlementRequestIdentityFilter settlementRequestIdentityFilter(
            SettlementSecurityErrorResponder errorResponder
    ) {
        return new SettlementRequestIdentityFilter(errorResponder);
    }

    @Bean
    public FilterRegistrationBean<SettlementRequestIdentityFilter> settlementIdentityFilterRegistration(
            SettlementRequestIdentityFilter filter
    ) {
        FilterRegistrationBean<SettlementRequestIdentityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain settlementSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            SettlementRequestIdentityFilter requestIdentityFilter,
            SettlementSecurityErrorResponder errorResponder
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, MANUAL_SETTLEMENT_RUN).hasRole(ADMIN_ROLE)
                        .requestMatchers(
                                HttpMethod.GET,
                                ADMIN_SETTLEMENTS_PATH,
                                ADMIN_SETTLEMENTS_PATH + "/**"
                        ).hasRole(ADMIN_ROLE)
                        .requestMatchers(
                                HttpMethod.GET,
                                SettlementRequestIdentityFilter.PUBLIC_SETTLEMENTS_PATH,
                                SettlementRequestIdentityFilter.PUBLIC_SETTLEMENTS_PATH + "/*"
                        ).hasRole(CREATOR_ROLE)
                        .requestMatchers(
                                SettlementRequestIdentityFilter.PUBLIC_SETTLEMENTS_PATH,
                                SettlementRequestIdentityFilter.PUBLIC_SETTLEMENTS_PATH + "/**"
                        ).denyAll()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponder.write(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        authenticationRequiredMessage(request.getRequestURI())
                                ))
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponder.write(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        authorityRequiredMessage(request.getRequestURI())
                                )))
                .addFilterAfter(requestIdentityFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> settlementJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(SettlementRequestIdentityFilter.ROLE_CLAIM);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    public JwtDecoder settlementJwtDecoder(SettlementJwtProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("Settlement JWT 서명 키가 설정되지 않았습니다.");
        }

        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Settlement JWT 서명 키가 Base64 형식이 아닙니다.", exception);
        }

        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static String authenticationRequiredMessage(String requestUri) {
        return isPublicSettlementPath(requestUri)
                ? SettlementRequestIdentityFilter.AUTHENTICATION_REQUIRED
                : AUTHENTICATION_REQUIRED;
    }

    private static String authorityRequiredMessage(String requestUri) {
        if (requestUri.equals(ADMIN_SETTLEMENTS_PATH)
                || requestUri.startsWith(ADMIN_SETTLEMENTS_PATH + "/")) {
            return ADMIN_AUTHORITY_REQUIRED;
        }
        return isPublicSettlementPath(requestUri)
                ? CREATOR_AUTHORITY_REQUIRED
                : ADMIN_AUTHORITY_REQUIRED;
    }

    private static boolean isPublicSettlementPath(String requestUri) {
        return requestUri.equals(SettlementRequestIdentityFilter.PUBLIC_SETTLEMENTS_PATH)
                || requestUri.startsWith(SettlementRequestIdentityFilter.PUBLIC_SETTLEMENTS_PATH + "/");
    }
}
