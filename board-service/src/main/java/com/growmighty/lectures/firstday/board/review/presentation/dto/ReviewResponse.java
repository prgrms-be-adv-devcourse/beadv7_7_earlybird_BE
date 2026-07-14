package com.growmighty.lectures.firstday.board.review.presentation.dto;

import com.growmighty.lectures.firstday.board.review.domain.Review;

import java.time.LocalDateTime;

public record ReviewResponse(Long id, Long projectId, Long userId, Integer rating, String content, LocalDateTime createdAt) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getProjectId(), review.getUserId(),
            review.getRating(), review.getContent(), review.getCreatedAt());
    }
}
