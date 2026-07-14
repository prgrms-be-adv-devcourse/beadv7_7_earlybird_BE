package com.growmighty.lectures.firstday.product.presentation.dto;

import com.growmighty.lectures.firstday.product.application.dto.RegisterProductCommand;
import lombok.NonNull;

import java.math.BigDecimal;

public record RegisterProductRequest(
        @NonNull Long sellerId,
        @NonNull String name,
        @NonNull BigDecimal price,
        @NonNull Integer stockQuantity,
        String description
) {
    public RegisterProductCommand toCommand() {
        return new RegisterProductCommand(sellerId, name, price, stockQuantity, description);
    }
}
