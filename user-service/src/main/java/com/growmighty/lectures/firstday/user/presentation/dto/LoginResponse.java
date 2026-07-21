package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import lombok.NonNull;

public record LoginResponse(@NonNull String accessToken, @NonNull String refreshToken, @NonNull UserResponse user) {
    public static LoginResponse of(@NonNull String accessToken, @NonNull String refreshToken, @NonNull UserInfo info) {
        return new LoginResponse(accessToken, refreshToken, UserResponse.from(info));
    }
}
