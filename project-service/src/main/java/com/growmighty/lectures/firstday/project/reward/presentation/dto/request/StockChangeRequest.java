package com.growmighty.lectures.firstday.project.reward.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** POST /internal/rewards/{rewardId}/decrease-stock, /restore-stock */
public record StockChangeRequest(
        @NotNull @Positive Integer quantity
) {
}
