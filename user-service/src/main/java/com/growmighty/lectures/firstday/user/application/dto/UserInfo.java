package com.growmighty.lectures.firstday.user.application.dto;

import com.growmighty.lectures.firstday.user.domain.User;
import com.growmighty.lectures.firstday.common.entity.UserRole;

public record UserInfo(
        Long id,
        String email,
        String name,
        String phoneNumber,
        UserRole role
) {
    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getName(), user.getPhoneNumber(), user.getRole());
    }
}
