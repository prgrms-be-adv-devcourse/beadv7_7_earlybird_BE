package com.growmighty.lectures.firstday.board.notice.application.dto;

public record RegisterProjectNoticeCommand(Long projectId, Long authorId, String authorName, String title, String content) {
}
