package com.growmighty.lectures.firstday.cart.application.port.dto;

import java.math.BigDecimal;

public record RewardSnapshot(
        Long rewardId,
        Long projectId,
        String rewardName,
        String projectName,
        BigDecimal price,
        Integer remainingQuantity,
        boolean orderable,
        boolean fallback
) {
    public RewardSnapshot(Long rewardId, Long projectId, String rewardName, String projectName,
                          BigDecimal price, Integer remainingQuantity, boolean orderable) {
        this(rewardId, projectId, rewardName, projectName, price, remainingQuantity, orderable, false);
    }

    public RewardSnapshot(Long rewardId, boolean orderable) {
        this(rewardId, null, null, null, BigDecimal.ZERO, null, orderable, true);
    }
}
