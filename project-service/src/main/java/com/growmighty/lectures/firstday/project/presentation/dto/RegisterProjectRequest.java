package com.growmighty.lectures.firstday.project.presentation.dto;

import com.growmighty.lectures.firstday.project.application.dto.RegisterProjectCommand;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RegisterProjectRequest(
        @NonNull Long creatorId,
        @NonNull String title,
        String description,
        @NonNull BigDecimal goalAmount,
        @NonNull LocalDateTime startAt,
        @NonNull LocalDateTime endAt
) {
    public RegisterProjectCommand toCommand() {
        return new RegisterProjectCommand(creatorId, title, description, goalAmount, startAt, endAt);
    }
}
