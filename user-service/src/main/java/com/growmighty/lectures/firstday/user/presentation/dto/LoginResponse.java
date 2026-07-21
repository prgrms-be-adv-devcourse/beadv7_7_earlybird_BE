package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.UserInfo;

public record LoginResponse(String accessToken, UserResponse user) {
    public static LoginResponse of(String accessToken, UserInfo info) {
        return new LoginResponse(accessToken, UserResponse.from(info));
    }
}
