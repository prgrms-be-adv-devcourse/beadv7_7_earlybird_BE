package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.UpdateProfileCommand;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank String name,
        @NotBlank String phoneNumber
) {
    public UpdateProfileCommand toCommand(Long userId) {
        return new UpdateProfileCommand(userId, name, phoneNumber);
    }
}
