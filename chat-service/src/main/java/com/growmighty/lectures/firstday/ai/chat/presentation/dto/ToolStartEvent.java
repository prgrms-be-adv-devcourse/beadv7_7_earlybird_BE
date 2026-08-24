package com.growmighty.lectures.firstday.ai.chat.presentation.dto;

public record ToolStartEvent(
    String toolName,
    int sequence,
    String message,
    String completedMessage
) {
}
