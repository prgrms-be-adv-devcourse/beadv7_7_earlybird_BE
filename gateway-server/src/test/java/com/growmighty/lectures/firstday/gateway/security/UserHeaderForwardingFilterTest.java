package com.growmighty.lectures.firstday.gateway.security;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserHeaderForwardingFilterTest {

    private final UserHeaderForwardingFilter filter = new UserHeaderForwardingFilter();

    @Test
    @DisplayName("인증된 요청은 JWT 의 subject/role 이 X-User-Id/X-User-Role 헤더로 다운스트림에 전달된다")
    void authenticatedRequest_forwardsTrustedHeaders() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/users/me"));
        ServerWebExchange[] captured = new ServerWebExchange[1];
        WebFilterChain chain = ex -> {
            captured[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuthentication("42", UserRole.BACKER.getCode())))
                .block();

        HttpHeaders headers = captured[0].getRequest().getHeaders();
        assertThat(headers.getFirst(JwtHeaders.USER_ID)).isEqualTo("42");
        assertThat(headers.getFirst(JwtHeaders.USER_ROLE)).isEqualTo(UserRole.BACKER.getCode());
    }

    @Test
    @DisplayName("클라이언트가 X-User-Id/X-User-Role 을 위조해도 검증된 토큰의 값으로 덮어써진다")
    void authenticatedRequest_stripsSpoofedHeaderBeforeSettingVerifiedValue() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/users/me")
                        .header(JwtHeaders.USER_ID, "999")
                        .header(JwtHeaders.USER_ROLE, UserRole.ADMIN.getCode()));
        ServerWebExchange[] captured = new ServerWebExchange[1];
        WebFilterChain chain = ex -> {
            captured[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuthentication("42", UserRole.BACKER.getCode())))
                .block();

        assertThat(captured[0].getRequest().getHeaders().get(JwtHeaders.USER_ID)).containsExactly("42");
        assertThat(captured[0].getRequest().getHeaders().get(JwtHeaders.USER_ROLE)).containsExactly(UserRole.BACKER.getCode());
    }

    @Test
    @DisplayName("인증 정보가 없는 요청(공개 경로)은 스푸핑 헤더만 제거된 채로 체인이 계속 진행된다")
    void unauthenticatedRequest_stripsHeadersAndContinuesChain() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/users/login")
                        .header(JwtHeaders.USER_ID, "999")
                        .header(JwtHeaders.USER_ROLE, UserRole.ADMIN.getCode()));
        ServerWebExchange[] captured = new ServerWebExchange[1];
        WebFilterChain chain = ex -> {
            captured[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getRequest().getHeaders().get(JwtHeaders.USER_ID)).isNull();
        assertThat(captured[0].getRequest().getHeaders().get(JwtHeaders.USER_ROLE)).isNull();
    }

    private JwtAuthenticationToken jwtAuthentication(String subject, String role) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(subject)
                .claim(JwtHeaders.ROLE_CLAIM, role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
