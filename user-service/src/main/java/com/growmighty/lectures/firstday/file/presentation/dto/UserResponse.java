package com.growmighty.lectures.firstday.file.presentation.dto;

import com.growmighty.lectures.firstday.file.application.dto.UserInfo;

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
