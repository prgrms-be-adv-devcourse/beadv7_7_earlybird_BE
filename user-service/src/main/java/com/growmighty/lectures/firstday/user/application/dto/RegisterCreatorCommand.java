package com.growmighty.lectures.firstday.user.application.dto;

public record RegisterCreatorCommand(
        Long userId,
        String bankCode,
        String accountNumber,
        String accountHolder
) {
}
