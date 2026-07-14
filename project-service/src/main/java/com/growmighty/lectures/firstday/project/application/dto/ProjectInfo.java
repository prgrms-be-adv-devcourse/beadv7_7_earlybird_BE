package com.growmighty.lectures.firstday.project.application.dto;

import com.growmighty.lectures.firstday.project.domain.Project;
import com.growmighty.lectures.firstday.project.domain.ProjectStatus;

import java.math.BigDecimal;

public record ProjectInfo(
        Long id,
        Long sellerId,
        String name,
        BigDecimal price,
        int stockQuantity,
        ProjectStatus status
) {
    public static ProjectInfo from(Project project) {
        return new ProjectInfo(
                project.getId(),
                project.getSellerId(),
                project.getName(),
                project.getPrice(),
                project.getStockQuantity(),
                project.getStatus());
    }

    public boolean isOrderable() {
        return this.status == ProjectStatus.ON_SALE;
    }
}
