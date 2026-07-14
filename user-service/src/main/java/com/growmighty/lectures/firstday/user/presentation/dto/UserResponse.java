package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.UserInfo;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phoneNumber
) {
    public static UserResponse from(UserInfo info) {
        return new UserResponse(info.id(), info.email(), info.name(), info.phoneNumber());
    }
}
