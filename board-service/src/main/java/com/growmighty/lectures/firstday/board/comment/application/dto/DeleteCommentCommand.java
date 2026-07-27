package com.growmighty.lectures.firstday.board.comment.application.dto;

import com.growmighty.lectures.firstday.common.entity.UserRole;

public record DeleteCommentCommand(Long commentId, Long requesterId, UserRole requesterRole) {
}