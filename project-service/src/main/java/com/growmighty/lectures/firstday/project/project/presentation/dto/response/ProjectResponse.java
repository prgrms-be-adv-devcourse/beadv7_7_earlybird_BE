package com.growmighty.lectures.firstday.project.project.presentation.dto.response;

import com.growmighty.lectures.firstday.project.project.domain.Project;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long projectId,
        Long creatorId,
        Long thumbnailId,
        String title,
        Long categoryId,
        String summary,
        String description,
        BigDecimal goalAmount,
        BigDecimal fundedAmount,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String status,
        boolean closed,
        String rejectReason,
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getProjectId(),
                project.getCreatorId(),
                project.getThumbnailId(),
                project.getTitle(),
                project.getCategoryId(),
                project.getSummary(),
                project.getDescription(),
                project.getGoalAmount(),
                project.getFundedAmount(),
                project.getStartAt(),
                project.getEndAt(),
                project.getStatus().name(),
                project.isClosed(),
                project.getRejectReason(),
                project.getSubmittedAt(),
                project.getApprovedAt(),
                project.getClosedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
