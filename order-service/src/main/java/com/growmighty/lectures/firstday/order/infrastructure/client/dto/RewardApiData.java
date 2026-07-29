package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;

public record RewardApiData(
        Long rewardId,
        Long projectId,
        String name,
        String description,
        BigDecimal price,
        Integer totalQuantity,
        Integer remainingQuantity,
        boolean orderable
) {
}
