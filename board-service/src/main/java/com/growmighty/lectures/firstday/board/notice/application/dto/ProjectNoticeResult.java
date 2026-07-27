package com.growmighty.lectures.firstday.board.notice.application.dto;

import com.growmighty.lectures.firstday.board.notice.domain.ProjectNotice;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeStatus;

import java.time.LocalDateTime;

public record ProjectNoticeResult(
        Long id,
        Long projectId,
        Long authorId,
        String authorName,
        String title,
        String content,
        Long viewCount,
        ProjectNoticeStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectNoticeResult from(ProjectNotice notice) {
        return new ProjectNoticeResult(
                notice.getId(),
                notice.getProjectId(),
                notice.getAuthorId(),
                notice.getAuthorName(),
                notice.getTitle(),
                notice.getContent(),
                notice.getViewCount(),
                notice.getStatus(),
                notice.getCreatedAt(),
                notice.getUpdatedAt());
    }
}