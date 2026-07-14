package com.growmighty.lectures.firstday.board.notice.presentation.dto;

import com.growmighty.lectures.firstday.board.notice.domain.Notice;

import java.time.LocalDateTime;

public record NoticeResponse(Long id, Long projectId, String title, String content, LocalDateTime createdAt) {
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(notice.getId(), notice.getProjectId(), notice.getTitle(),
            notice.getContent(), notice.getCreatedAt());
    }
}
