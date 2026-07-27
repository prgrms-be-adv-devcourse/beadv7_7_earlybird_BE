package com.growmighty.lectures.firstday.board.comment.application.dto;

public record UpdateCommentCommand(Long commentId, Long requesterId, String content) {
}