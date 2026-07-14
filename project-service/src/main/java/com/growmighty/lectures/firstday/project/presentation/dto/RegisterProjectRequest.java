package com.growmighty.lectures.firstday.project.presentation.dto;

import com.growmighty.lectures.firstday.project.application.dto.RegisterProjectCommand;
import lombok.NonNull;

import java.math.BigDecimal;

public record RegisterProjectRequest(
        @NonNull Long sellerId,
        @NonNull String name,
        @NonNull BigDecimal price,
        @NonNull Integer stockQuantity,
        String description
) {
    public RegisterProjectCommand toCommand() {
        return new RegisterProjectCommand(sellerId, name, price, stockQuantity, description);
    }
}
