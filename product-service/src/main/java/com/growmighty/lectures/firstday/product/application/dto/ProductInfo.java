package com.growmighty.lectures.firstday.product.application.dto;

import com.growmighty.lectures.firstday.product.domain.Product;
import com.growmighty.lectures.firstday.product.domain.ProductStatus;

import java.math.BigDecimal;

public record ProductInfo(
        Long id,
        Long sellerId,
        String name,
        BigDecimal price,
        int stockQuantity,
        ProductStatus status
) {
    public static ProductInfo from(Product product) {
        return new ProductInfo(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStatus());
    }

    public boolean isOrderable() {
        return this.status == ProductStatus.ON_SALE;
    }
}
