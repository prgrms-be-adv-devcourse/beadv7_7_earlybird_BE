package com.growmighty.lectures.firstday.order.application.port.dto;

import java.math.BigDecimal;

public record RewardSnapshot(
        Long rewardId,
        Long projectId,
        String name,
        BigDecimal price,
        int remainingQuantity,
        boolean orderable
) {
}
