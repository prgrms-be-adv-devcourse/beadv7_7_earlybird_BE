package com.growmighty.lectures.firstday.cart.application.port.dto;

import java.math.BigDecimal;

public record RewardSnapshot(
        Long rewardId,
        Long projectId,
        String rewardName,
        String projectName,
        BigDecimal price,
        Integer remainingQuantity,
        boolean orderable
) {
    public RewardSnapshot(Long rewardId, boolean orderable) {
        this(rewardId, null, null, null, BigDecimal.ZERO, null, orderable);
    }
}
