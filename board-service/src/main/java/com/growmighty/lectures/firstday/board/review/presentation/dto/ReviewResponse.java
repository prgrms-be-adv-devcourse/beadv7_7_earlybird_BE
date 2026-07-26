package com.growmighty.lectures.firstday.board.review.presentation.dto;

import com.growmighty.lectures.firstday.board.review.application.dto.ReviewResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id, Long projectId, Long rewardId, String rewardName, String authorName,
        BigDecimal rating, String content, LocalDateTime createdAt) {
    public static ReviewResponse from(ReviewResult result) {
        return new ReviewResponse(result.id(), result.projectId(), result.rewardId(), result.rewardName(),
            result.authorName(), result.rating(), result.content(), result.createdAt());
    }
}