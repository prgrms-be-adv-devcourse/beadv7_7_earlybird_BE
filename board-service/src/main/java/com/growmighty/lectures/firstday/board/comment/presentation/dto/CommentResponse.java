package com.growmighty.lectures.firstday.board.comment.presentation.dto;

import com.growmighty.lectures.firstday.board.comment.domain.Comment;

import java.time.LocalDateTime;

public record CommentResponse(Long id, Long projectId, Long userId, Long parentId, String content, LocalDateTime createdAt) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getProjectId(), comment.getUserId(),
            comment.getParentId(), comment.getContent(), comment.getCreatedAt());
    }
}
