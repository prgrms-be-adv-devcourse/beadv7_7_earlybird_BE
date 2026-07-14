package com.growmighty.lectures.firstday.cart.infrastructure.client.dto;

import java.math.BigDecimal;

/** product-service 의 GET /products/{id} 응답 data 부분. */
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
