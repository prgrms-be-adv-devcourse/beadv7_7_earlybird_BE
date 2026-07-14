package com.growmighty.lectures.firstday.project.application.dto;

import com.growmighty.lectures.firstday.project.domain.Project;
import com.growmighty.lectures.firstday.project.domain.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProjectInfo(
        Long id,
        Long creatorId,
        String title,
        String description,
        BigDecimal goalAmount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        ProjectStatus status
) {
    public static ProjectInfo from(Project project) {
        return new ProjectInfo(
                project.getId(),
                project.getCreatorId(),
                project.getTitle(),
                project.getDescription(),
                project.getGoalAmount(),
                project.getStartAt(),
                project.getEndAt(),
                project.getStatus());
    }

    public boolean isOrderable() {
        return this.status == ProjectStatus.OPEN;
    }
}
