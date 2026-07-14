package com.growmighty.lectures.firstday.user.application.dto;

public record RegisterUserCommand(
        String email,
        String rawPassword,
        String name,
        String phoneNumber
) {
}
