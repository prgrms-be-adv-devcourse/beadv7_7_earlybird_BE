package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.ChangePasswordCommand;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
    public ChangePasswordCommand toCommand(Long userId) {
        return new ChangePasswordCommand(userId, currentPassword, newPassword);
    }
}
