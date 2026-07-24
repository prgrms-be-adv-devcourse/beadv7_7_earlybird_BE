package com.growmighty.lectures.firstday.project.reward.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * PATCH /api/v1/rewards/{rewardId}/quantity (관리자 전용).
 * 공개(진행중) 리워드의 수량을 부득이하게 줄여야 할 때만 사용 — 관리자 전용.
 */
public record RewardQuantityDecreaseRequest(
        @NotNull @Positive Integer amount
) {
}
