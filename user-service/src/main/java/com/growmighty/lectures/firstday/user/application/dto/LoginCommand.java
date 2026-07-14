package com.growmighty.lectures.firstday.user.application.dto;

public record LoginCommand(String email, String rawPassword) {
}
