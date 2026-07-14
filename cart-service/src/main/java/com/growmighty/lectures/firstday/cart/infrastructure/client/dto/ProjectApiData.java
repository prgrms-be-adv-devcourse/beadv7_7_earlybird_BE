package com.growmighty.lectures.firstday.cart.infrastructure.client.dto;

import java.math.BigDecimal;

/** project-service 의 GET /projects/{id} 응답 data 부분. */
public record ProjectApiData(
        Long id,
        Long sellerId,
        String name,
        BigDecimal price,
        int stockQuantity,
        String status,
        boolean orderable
) {
}
