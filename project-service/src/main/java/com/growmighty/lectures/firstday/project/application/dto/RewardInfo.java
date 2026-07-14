package com.growmighty.lectures.firstday.project.application.dto;

import com.growmighty.lectures.firstday.project.domain.Reward;

import java.math.BigDecimal;

public record RewardInfo(
        Long id,
        Long projectId,
        String name,
        String description,
        BigDecimal price,
        Integer totalQuantity,
        Integer remainingQuantity
) {
    public static RewardInfo from(Reward reward) {
        return new RewardInfo(
                reward.getId(),
                reward.getProjectId(),
                reward.getName(),
                reward.getDescription(),
                reward.getPrice(),
                reward.getTotalQuantity(),
                reward.getRemainingQuantity());
    }

    public boolean isOrderable() {
        return this.remainingQuantity > 0;
    }
}
