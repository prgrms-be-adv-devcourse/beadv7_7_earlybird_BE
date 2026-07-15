package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.RegisterCreatorCommand;
import lombok.NonNull;

public record RegisterCreatorRequest(
        @NonNull String bankName,
        @NonNull String accountNumber,
        @NonNull String accountHolder
) {
    public RegisterCreatorCommand toCommand(Long userId) {
        return new RegisterCreatorCommand(userId, bankName, accountNumber, accountHolder);
    }
}
