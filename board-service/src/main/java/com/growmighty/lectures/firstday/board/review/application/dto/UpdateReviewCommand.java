package com.growmighty.lectures.firstday.board.review.application.dto;

import java.math.BigDecimal;

public record UpdateReviewCommand(Long reviewId, Long requesterId, BigDecimal rating, String content) {
}