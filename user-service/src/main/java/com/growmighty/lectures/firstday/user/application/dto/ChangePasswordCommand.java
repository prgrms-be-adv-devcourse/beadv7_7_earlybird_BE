package com.growmighty.lectures.firstday.user.application.dto;

public record ChangePasswordCommand(
        Long userId,
        String currentPassword,
        String newPassword
) {
}
