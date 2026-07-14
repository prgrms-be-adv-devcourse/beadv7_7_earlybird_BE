package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.RegisterUserCommand;
import lombok.NonNull;

public record RegisterUserRequest(
        @NonNull String email,
        @NonNull String password,
        @NonNull String name,
        @NonNull String phoneNumber
) {
    public RegisterUserCommand toCommand() {
        return new RegisterUserCommand(email, password, name, phoneNumber);
    }
}
