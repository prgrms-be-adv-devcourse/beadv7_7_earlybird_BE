package com.growmighty.lectures.firstday.project.reward.presentation.dto.response;

import com.growmighty.lectures.firstday.project.reward.domain.Reward;

import java.math.BigDecimal;

public record RewardResponse(
        Long rewardId,
        Long projectId,
        String name,
        String description,
        BigDecimal price,
        Integer totalQuantity,
        Integer remainingQuantity,
        boolean orderable
) {
    public static RewardResponse from(Reward reward) {
        return new RewardResponse(
                reward.getRewardId(),
                reward.getProjectId(),
                reward.getName(),
                reward.getDescription(),
                reward.getPrice(),
                reward.getTotalQuantity(),
                reward.getRemainingQuantity(),
                reward.isOrderable());
    }
}
