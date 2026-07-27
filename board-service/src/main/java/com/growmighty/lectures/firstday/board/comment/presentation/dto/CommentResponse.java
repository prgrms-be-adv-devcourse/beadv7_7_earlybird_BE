package com.growmighty.lectures.firstday.board.comment.presentation.dto;

import com.growmighty.lectures.firstday.board.comment.application.dto.CommentResult;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;

import java.time.LocalDateTime;
import java.util.List;

public record CommentResponse(
        Long id, CommentTargetType targetType, Long targetId, String authorName, Long parentId, String content,
        LocalDateTime createdAt, List<CommentResponse> replies) {
    public static CommentResponse from(CommentResult result) {
        return new CommentResponse(result.id(), result.targetType(), result.targetId(), result.authorName(),
            result.parentId(), result.content(), result.createdAt(),
            result.replies().stream().map(CommentResponse::from).toList());
    }

    public static List<CommentResponse> from(List<CommentResult> results) {
        return results.stream().map(CommentResponse::from).toList();
    }
}