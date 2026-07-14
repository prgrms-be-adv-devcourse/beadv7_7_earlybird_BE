package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;

public record ProductApiData(
        Long id,
        Long sellerId,
        String name,
        BigDecimal price,
        int stockQuantity,
        String status,
        boolean orderable
) {
}
