package com.growmighty.lectures.firstday.project.presentation.dto;

import com.growmighty.lectures.firstday.project.application.dto.RewardInfo;

import java.math.BigDecimal;

public record RewardResponse(
        Long id,
        Long projectId,
        String name,
        String description,
        BigDecimal price,
        Integer totalQuantity,
        Integer remainingQuantity,
        boolean orderable
) {
    public static RewardResponse from(RewardInfo info) {
        return new RewardResponse(
                info.id(),
                info.projectId(),
                info.name(),
                info.description(),
                info.price(),
                info.totalQuantity(),
                info.remainingQuantity(),
                info.isOrderable());
    }
}
