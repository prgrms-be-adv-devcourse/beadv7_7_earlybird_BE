package com.growmighty.lectures.firstday.project.project.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** PUT /internal/v1/projects/{projectId}/funded-amount — 절대값(누적 총액) 덮어쓰기, 멱등. */
public record FundedAmountUpdateRequest(
        @NotNull @PositiveOrZero BigDecimal fundedAmount
) {
}
