package com.growmighty.lectures.firstday.project.application.dto;

import java.math.BigDecimal;

public record RegisterRewardCommand(
        Long projectId,
        String name,
        String description,
        BigDecimal price,
        Integer totalQuantity
) {
}
