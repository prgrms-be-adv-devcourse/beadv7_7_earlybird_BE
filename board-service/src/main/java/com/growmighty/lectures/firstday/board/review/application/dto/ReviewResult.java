package com.growmighty.lectures.firstday.board.review.application.dto;

import com.growmighty.lectures.firstday.board.review.domain.Review;
import com.growmighty.lectures.firstday.board.review.domain.ReviewStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewResult(
        Long id,
        Long projectId,
        Long rewardId,
        String rewardName,
        Long authorId,
        String authorName,
        BigDecimal rating,
        String content,
        ReviewStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> photoUrls
) {
    public static ReviewResult from(Review review) {
        return from(review, List.of());
    }

    public static ReviewResult from(Review review, List<String> photoUrls) {
        return new ReviewResult(
                review.getId(),
                review.getProjectId(),
                review.getRewardId(),
                review.getRewardName(),
                review.getAuthorId(),
                review.getAuthorName(),
                review.getRating().getValue(),
                review.getContent(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                photoUrls);
    }
}
