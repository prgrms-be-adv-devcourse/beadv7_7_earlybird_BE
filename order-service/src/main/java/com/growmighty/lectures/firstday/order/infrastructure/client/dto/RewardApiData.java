package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;

public record RewardApiData(
        Long id,
        Long projectId,
        String name,
        String description,
        BigDecimal price,
        Integer totalQuantity,
        Integer remainingQuantity,
        boolean orderable
) {
}
