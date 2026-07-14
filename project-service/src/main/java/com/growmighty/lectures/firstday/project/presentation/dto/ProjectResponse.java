package com.growmighty.lectures.firstday.project.presentation.dto;

import com.growmighty.lectures.firstday.project.application.dto.ProjectInfo;

import java.math.BigDecimal;

public record ProjectResponse(
        Long id,
        Long sellerId,
        String name,
        BigDecimal price,
        int stockQuantity,
        String status,
        boolean orderable
) {
    public static ProjectResponse from(ProjectInfo info) {
        return new ProjectResponse(
                info.id(),
                info.sellerId(),
                info.name(),
                info.price(),
                info.stockQuantity(),
                info.status().name(),
                info.isOrderable());
    }
}
