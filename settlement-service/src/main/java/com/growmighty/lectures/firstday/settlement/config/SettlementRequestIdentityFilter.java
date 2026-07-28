package com.growmighty.lectures.firstday.settlement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public final class SettlementRequestIdentityFilter extends OncePerRequestFilter {

    static final String PUBLIC_SETTLEMENTS_PATH = "/api/v1/settlements";
    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";
    static final String ROLE_CLAIM = "role";
    static final String AUTHENTICATION_REQUIRED = "유효한 사용자 인증이 필요합니다.";

    private final SettlementSecurityErrorResponder errorResponder;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals(PUBLIC_SETTLEMENTS_PATH)
                && !path.startsWith(PUBLIC_SETTLEMENTS_PATH + "/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            reject(response);
            return;
        }

        Jwt jwt = jwtAuthentication.getToken();
        String subject;
        String role;
        try {
            subject = jwt.getSubject();
            role = jwt.getClaimAsString(ROLE_CLAIM);
        } catch (RuntimeException exception) {
            reject(response);
            return;
        }

        List<String> userIdHeaders = headerValues(request, USER_ID_HEADER);
        List<String> userRoleHeaders = headerValues(request, USER_ROLE_HEADER);
        if (!isValidUserId(subject)
                || role == null || role.isBlank()
                || userIdHeaders.size() != 1
                || userRoleHeaders.size() != 1
                || !subject.equals(userIdHeaders.getFirst())
                || !role.equals(userRoleHeaders.getFirst())) {
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static List<String> headerValues(HttpServletRequest request, String name) {
        return Collections.list(request.getHeaders(name));
    }

    private static boolean isValidUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            return false;
        }
        try {
            long userId = Long.parseLong(subject);
            return userId > 0 && subject.equals(Long.toString(userId));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        errorResponder.write(response, HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED);
    }
}
