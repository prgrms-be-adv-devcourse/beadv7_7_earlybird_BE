package com.growmighty.lectures.firstday.user.application.dto;

import com.growmighty.lectures.firstday.user.domain.User;

public record UserInfo(
        Long id,
        String email,
        String name,
        String phoneNumber
) {
    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getName(), user.getPhoneNumber());
    }
}
