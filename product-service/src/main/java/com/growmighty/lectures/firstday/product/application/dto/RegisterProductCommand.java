package com.growmighty.lectures.firstday.product.application.dto;

import java.math.BigDecimal;

public record RegisterProductCommand(
        Long sellerId,
        String name,
        BigDecimal price,
        int stockQuantity,
        String description
) {
}
