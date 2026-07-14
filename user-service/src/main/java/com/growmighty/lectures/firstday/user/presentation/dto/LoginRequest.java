package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.LoginCommand;
import lombok.NonNull;

public record LoginRequest(@NonNull String email, @NonNull String password) {
    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
