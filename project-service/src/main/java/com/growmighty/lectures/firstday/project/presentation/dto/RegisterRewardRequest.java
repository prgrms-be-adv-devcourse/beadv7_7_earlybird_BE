package com.growmighty.lectures.firstday.project.presentation.dto;

import com.growmighty.lectures.firstday.project.application.dto.RegisterRewardCommand;
import lombok.NonNull;

import java.math.BigDecimal;

public record RegisterRewardRequest(
        @NonNull String name,
        String description,
        @NonNull BigDecimal price,
        @NonNull Integer totalQuantity
) {
    public RegisterRewardCommand toCommand(Long projectId) {
        return new RegisterRewardCommand(projectId, name, description, price, totalQuantity);
    }
}
