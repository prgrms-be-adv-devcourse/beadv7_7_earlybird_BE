package com.growmighty.lectures.firstday.board.comment.presentation.dto;

import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id, CommentTargetType targetType, Long targetId, Long authorId, Long parentId, String content,
        LocalDateTime createdAt) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getTargetType(), comment.getTargetId(),
            comment.getAuthorId(), comment.getParentId(), comment.getContent(), comment.getCreatedAt());
    }
}
