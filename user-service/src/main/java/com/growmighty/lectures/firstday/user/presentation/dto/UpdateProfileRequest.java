package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.UpdateProfileCommand;
import lombok.NonNull;

public record UpdateProfileRequest(
        @NonNull String name,
        @NonNull String phoneNumber
) {
    public UpdateProfileCommand toCommand(Long userId) {
        return new UpdateProfileCommand(userId, name, phoneNumber);
    }
}
