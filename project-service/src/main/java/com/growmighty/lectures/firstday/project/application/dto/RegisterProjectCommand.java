package com.growmighty.lectures.firstday.project.application.dto;

import java.math.BigDecimal;

public record RegisterProjectCommand(
        Long sellerId,
        String name,
        BigDecimal price,
        int stockQuantity,
        String description
) {
}
