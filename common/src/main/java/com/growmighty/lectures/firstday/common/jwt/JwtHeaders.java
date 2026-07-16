package com.growmighty.lectures.firstday.common.jwt;

/** user-service(발급)와 gateway-server(검증)가 공유하는 헤더/클레임 이름의 단일 출처. */
public final class JwtHeaders {
    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String ROLE_CLAIM = "role";

    private JwtHeaders() {
    }
}
