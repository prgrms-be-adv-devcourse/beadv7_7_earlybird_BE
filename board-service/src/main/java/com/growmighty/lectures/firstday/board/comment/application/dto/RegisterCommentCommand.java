package com.growmighty.lectures.firstday.board.comment.application.dto;

import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;

public record RegisterCommentCommand(CommentTargetType targetType, Long targetId, Long authorId, String content) {
}