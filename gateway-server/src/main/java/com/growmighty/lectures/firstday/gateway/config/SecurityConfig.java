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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

import static com.growmighty.lectures.firstday.common.entity.UserRole.*;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    public static final String URI_PREFIX_API = "/api/v1";
    public static final String URI_PREFIX_INTERNAL = "/internal/v1";

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        // 인증 불필요
                        // k8s livenessProbe/readinessProbe/startupProbe가 인증 없이 호출한다 -
                        // 여기 빠지면 401을 fail로 해석해 정상 기동 중인 파드를 계속 재시작시킨다.
                        // health 하위 경로(liveness/readiness 그룹)까지 포함, gateway/refresh 등
                        // 다른 actuator 엔드포인트는 계속 인증 필요.
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
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

                        // chat
                        //// 챗봇 - 로그인/비로그인 사용자 모두 호출 가능 (optional-auth). 로그인 시에는
                        //// UserHeaderForwardingFilter 가 X-User-Id/X-User-Role 을 실어 보내고, 비로그인
                        //// 시에는 헤더 없이 통과 - 구분은 chat-service 쪽에서 anonId 로 처리한다.
                        .pathMatchers(HttpMethod.POST, URI_PREFIX_API + "/chat/**").permitAll()

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
                        .pathMatchers(HttpMethod.DELETE, URI_PREFIX_API + "/project-categories/*").hasRole(ADMIN.getCode())

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

                        // files
                        //// 프로젝트 썸네일 등 전부 공개 콘텐츠 - 조회만 비로그인 허용, 등록/삭제는 인증 필요
                        .pathMatchers(HttpMethod.GET, URI_PREFIX_API + "/files").permitAll()

                        // settlements
                        .pathMatchers(HttpMethod.GET,
                                URI_PREFIX_API + "/settlements/all",
                                URI_PREFIX_API + "/settlements/all/**").hasRole(ADMIN.getCode())
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

    /** 브라우저에서 오는 크로스 오리진 요청에 CORS 헤더를 실어 보낸다 — 허용 오리진은 cors.allowed-origins(config-server) 로 관리. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
