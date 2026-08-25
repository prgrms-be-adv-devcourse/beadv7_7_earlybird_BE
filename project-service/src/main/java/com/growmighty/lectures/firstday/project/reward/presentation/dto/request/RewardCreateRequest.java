package com.growmighty.lectures.firstday.project.reward.presentation.dto.request;

import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * POST /api/v1/projects/{projectId}/rewards.
 * totalQuantity를 비워두면 무제한 리워드로 등록된다.
 */
public record RewardCreateRequest(
        @NotBlank String name,
        String description,
        @NotNull BigDecimal price,
        @PositiveOrZero Integer totalQuantity,
        // Project.idempotencyKey와 동일 계약 — 재시도 시 같은 키를 재전송해야 중복 생성되지 않는다.
        // 미전송 클라이언트와의 하위 호환을 위해 필수는 아니다 — 미전송 시 재시도 중복 방지는
        // 적용되지 않는다(기존 동작과 동일).
        UUID idempotencyKey
) {
    public Reward toEntity(Long projectId) {
        UUID key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID();
        return Reward.register(projectId, key, name, description, price, totalQuantity);
    }
}
