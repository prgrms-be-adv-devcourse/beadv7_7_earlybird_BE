package com.growmighty.lectures.firstday.board.notice.presentation.dto;

import com.growmighty.lectures.firstday.board.notice.application.dto.ProjectNoticeResult;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectNoticeResponse(Long id, Long projectId, String authorName, String title, String content, Long viewCount, LocalDateTime createdAt) {
    public static ProjectNoticeResponse from(ProjectNoticeResult result) {
        return new ProjectNoticeResponse(result.id(), result.projectId(), result.authorName(), result.title(),
            result.content(), result.viewCount(), result.createdAt());
    }

    public static List<ProjectNoticeResponse> from(List<ProjectNoticeResult> results) {
        return results.stream().map(ProjectNoticeResponse::from).toList();
    }
}
