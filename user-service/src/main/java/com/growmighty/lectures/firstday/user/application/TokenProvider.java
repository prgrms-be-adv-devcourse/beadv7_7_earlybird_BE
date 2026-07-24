package com.growmighty.lectures.firstday.user.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;

public interface TokenProvider {
    String issueAccessToken(Long userId, UserRole role);

    String issueRefreshToken(Long userId);

    /** 리프레시 토큰을 검증하고 subject(userId)를 반환한다. 서명·만료·타입이 유효하지 않으면 예외를 던진다. */
    Long parseRefreshToken(String refreshToken);
}
