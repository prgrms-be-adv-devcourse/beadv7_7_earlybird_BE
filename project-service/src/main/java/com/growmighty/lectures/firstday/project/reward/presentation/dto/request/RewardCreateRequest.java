package com.growmighty.lectures.firstday.project.reward.presentation.dto.request;

import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * POST /api/v1/projects/{projectId}/rewards.
 * totalQuantity를 비워두면 무제한 리워드로 등록된다.
 */
public record RewardCreateRequest(
        @NotBlank String name,
        String description,
        @NotNull BigDecimal price,
        @PositiveOrZero Integer totalQuantity
) {
    public Reward toEntity(Long projectId) {
        return Reward.register(projectId, name, description, price, totalQuantity);
    }
}
