package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.UpdateProfileCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record UpdateProfileRequest(
        @NotBlank String name,
        @NotBlank String phoneNumber,
        String currentPassword,
        @Length(min = 4) String newPassword
) {
    @AssertTrue(message = "비밀번호를 변경하려면 현재 비밀번호를 함께 입력해야 합니다.")
    private boolean isCurrentPasswordPresentWhenChangingPassword() {
        return newPassword == null || currentPassword != null;
    }

    public UpdateProfileCommand toCommand(Long userId) {
        return new UpdateProfileCommand(userId, name, phoneNumber, currentPassword, newPassword);
    }
}
