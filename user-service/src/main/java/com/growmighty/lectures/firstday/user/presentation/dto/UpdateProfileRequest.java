package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.UpdateProfileCommand;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record UpdateProfileRequest(
        @NotBlank String name,
        @NotBlank String phoneNumber,
        @NotBlank String currentPassword,
        @NotBlank @Length(min = 4) String newPassword
) {
    public UpdateProfileCommand toCommand(Long userId) {
        return new UpdateProfileCommand(userId, name, phoneNumber, currentPassword, newPassword);
    }
}
