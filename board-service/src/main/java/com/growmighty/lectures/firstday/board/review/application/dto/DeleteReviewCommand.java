package com.growmighty.lectures.firstday.board.review.application.dto;

import com.growmighty.lectures.firstday.common.entity.UserRole;

public record DeleteReviewCommand(Long reviewId, Long requesterId, UserRole requesterRole) {
}