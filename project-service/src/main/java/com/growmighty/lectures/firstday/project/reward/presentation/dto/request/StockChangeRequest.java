package com.growmighty.lectures.firstday.project.reward.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * POST /internal/rewards/{rewardId}/decrease-stock, /restore-stock
 * orderId는 (orderId, rewardId, operation) 멱등키의 일부다(#195) — 필수값이며, order-service가
 * 이 필드를 실어 보내는 변경은 GitHub #200으로 별도 추적한다.
 */
public record StockChangeRequest(
        @NotNull @Positive Integer quantity,
        @NotNull Long orderId
) {
}
