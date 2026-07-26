package com.growmighty.lectures.firstday.board.review.application.dto;

import java.math.BigDecimal;

public record RegisterReviewCommand(Long projectId, Long rewardId, Long authorId, BigDecimal rating, String content) {
}