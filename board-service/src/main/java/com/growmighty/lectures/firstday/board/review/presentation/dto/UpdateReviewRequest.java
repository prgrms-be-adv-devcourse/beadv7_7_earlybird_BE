package com.growmighty.lectures.firstday.board.review.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateReviewRequest(@NotNull BigDecimal rating, String content) {
}