package com.growmighty.lectures.firstday.project.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RegisterProjectCommand(
        Long creatorId,
        String title,
        String description,
        BigDecimal goalAmount,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
