package com.growmighty.lectures.firstday.user.application;

import com.growmighty.lectures.firstday.user.domain.UserRole;

public interface TokenProvider {
    String issueAccessToken(Long userId, UserRole role);
}
