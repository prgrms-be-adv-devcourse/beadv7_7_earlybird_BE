package com.growmighty.lectures.firstday.user.application.dto;

public record UpdateProfileCommand(
        Long userId,
        String name,
        String phoneNumber,
        String currentPassword,
        String newPassword
) {
}
