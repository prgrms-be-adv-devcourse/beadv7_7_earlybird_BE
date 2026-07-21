package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import com.growmighty.lectures.firstday.common.entity.UserRole;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phoneNumber,
        UserRole role
) {
    public static UserResponse from(UserInfo info) {
        return new UserResponse(info.id(), info.email(), info.name(), info.phoneNumber(), info.role());
    }
}
