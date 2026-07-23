package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.RegisterCreatorCommand;
import jakarta.validation.constraints.NotBlank;

public record RegisterCreatorRequest(
        @NotBlank String bankName,
        @NotBlank String accountNumber,
        @NotBlank String accountHolder
) {
    public RegisterCreatorCommand toCommand(Long userId) {
        return new RegisterCreatorCommand(userId, bankName, accountNumber, accountHolder);
    }
}
