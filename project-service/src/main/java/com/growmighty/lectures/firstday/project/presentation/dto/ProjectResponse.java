package com.growmighty.lectures.firstday.project.presentation.dto;

import com.growmighty.lectures.firstday.project.application.dto.ProjectInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        Long creatorId,
        Long categoryId,
        String title,
        String description,
        BigDecimal goalAmount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        boolean orderable
) {
    public static ProjectResponse from(ProjectInfo info) {
        return new ProjectResponse(
                info.id(),
                info.creatorId(),
                info.categoryId(),
                info.title(),
                info.description(),
                info.goalAmount(),
                info.startAt(),
                info.endAt(),
                info.status().name(),
                info.isOrderable());
    }
}
