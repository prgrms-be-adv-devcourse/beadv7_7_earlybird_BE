package com.growmighty.lectures.firstday.file.application.dto;

public record RegisterUserCommand(
        String email,
        String rawPassword,
        String name,
        String phoneNumber
) {
}
