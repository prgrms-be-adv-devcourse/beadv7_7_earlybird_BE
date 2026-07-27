package com.growmighty.lectures.firstday.board.comment.application.dto;

import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentStatus;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;

import java.time.LocalDateTime;
import java.util.List;

public record CommentResult(
        Long id,
        CommentTargetType targetType,
        Long targetId,
        Long authorId,
        String authorName,
        Long parentId,
        String content,
        CommentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CommentResult> replies
) {
    /** 루트 댓글 변환용 — 대댓글은 1단으로 구조적으로 고정돼 있어 replies 안의 항목은 항상 빈 리스트로 만든다. */
    public static CommentResult from(Comment comment, List<Comment> replies) {
        return new CommentResult(
                comment.getId(),
                comment.getTargetType(),
                comment.getTargetId(),
                comment.getAuthorId(),
                comment.getAuthorName(),
                comment.getParentId(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replies.stream().map(reply -> CommentResult.from(reply, List.of())).toList());
    }

    /** 답글/수정처럼 단건만 다루는 유스케이스용 — replies는 항상 빈 리스트 */
    public static CommentResult from(Comment comment) {
        return from(comment, List.of());
    }
}