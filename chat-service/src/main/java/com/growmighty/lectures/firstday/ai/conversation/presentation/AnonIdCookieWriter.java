package com.growmighty.lectures.firstday.ai.conversation.presentation;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AnonIdCookieWriter {
    public static final String COOKIE_NAME = "anonId";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(7);

    public void write(HttpServletResponse response, String anonId) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, anonId)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(COOKIE_MAX_AGE)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
