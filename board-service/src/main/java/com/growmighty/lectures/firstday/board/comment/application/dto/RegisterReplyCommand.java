package com.growmighty.lectures.firstday.board.comment.application.dto;

public record RegisterReplyCommand(Long parentId, Long authorId, String content) {
}