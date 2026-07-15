package com.growmighty.lectures.firstday.user.application.dto;

public record RegisterCreatorCommand(
        Long userId,
        String bankName,
        String accountNumber,
        String accountHolder
) {
}
