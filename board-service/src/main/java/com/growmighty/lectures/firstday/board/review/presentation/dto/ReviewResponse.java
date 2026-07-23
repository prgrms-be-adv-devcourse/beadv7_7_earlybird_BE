package com.growmighty.lectures.firstday.board.review.presentation.dto;

import com.growmighty.lectures.firstday.board.review.domain.Review;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewResponse(Long id, Long projectId, Long orderId, Long authorId, BigDecimal rating, String content, LocalDateTime createdAt) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getProjectId(), review.getOrderId(), review.getAuthorId(),
            review.getRating().getValue(), review.getContent(), review.getCreatedAt());
    }
}
