package com.growmighty.lectures.firstday.file.presentation.dto;

import com.growmighty.lectures.firstday.file.application.dto.LoginCommand;
import lombok.NonNull;

public record LoginRequest(@NonNull String email, @NonNull String password) {
    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
