package com.growmighty.lectures.firstday.gateway.security;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 인증된 JWT 의 클레임을 X-User-Id/X-User-Role 헤더로 변환해 다운스트림 서비스에 전달한다.
 * 클라이언트가 이 헤더를 위조해 보내더라도 항상 먼저 제거한 뒤, 검증에 성공한 경우에만
 * 신뢰할 수 있는 값으로 다시 채운다 — Spring Security 는 헤더 위조 방어까지 대신해주지 않는
 * 유일한 부분이라 여기만 직접 구현한다.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class UserHeaderForwardingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Mono<Void> never carries a value (even on success), so switchIfEmpty/defaultIfEmpty
        // must operate on the Mono<ServerWebExchange> below — chaining a fallback directly after
        // a Mono<Void> would fire the fallback on top of every successful call too.
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> resolveExchange(exchange, auth))
                .defaultIfEmpty(stripped(exchange))
                .flatMap(chain::filter);
    }

    private ServerWebExchange resolveExchange(ServerWebExchange exchange, Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return stripped(exchange);
        }
        Jwt jwt = jwtAuth.getToken();
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(JwtHeaders.USER_ID);
                    h.remove(JwtHeaders.USER_ROLE);
                })
                .header(JwtHeaders.USER_ID, jwt.getSubject())
                .header(JwtHeaders.USER_ROLE, jwt.getClaimAsString(JwtHeaders.ROLE_CLAIM))
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private ServerWebExchange stripped(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(JwtHeaders.USER_ID);
                    h.remove(JwtHeaders.USER_ROLE);
                })
                .build();
        return exchange.mutate().request(request).build();
    }
}
