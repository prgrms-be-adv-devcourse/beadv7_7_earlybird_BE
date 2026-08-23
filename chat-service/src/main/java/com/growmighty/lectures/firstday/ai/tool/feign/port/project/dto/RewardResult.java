package com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto;

import java.math.BigDecimal;

public record RewardResult(
    Long rewardId,
    Long projectId,
    String name,
    String description,
    BigDecimal price,
    Integer totalQuantity,
    Integer remainingQuantity,
    boolean orderable,
    boolean active
) {
}
