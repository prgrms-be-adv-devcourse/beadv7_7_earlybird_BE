package com.growmighty.lectures.firstday.order.application.port.dto;

import java.math.BigDecimal;

public record ProductSnapshot(
        Long productId,
        String name,
        BigDecimal price,
        int stockQuantity,
        boolean orderable
) {
}
