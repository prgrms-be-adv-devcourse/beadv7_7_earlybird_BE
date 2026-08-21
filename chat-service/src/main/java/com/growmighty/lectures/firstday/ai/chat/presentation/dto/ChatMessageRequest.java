package com.growmighty.lectures.firstday.ai.chat.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
    @NotBlank
    String message
) {
}
