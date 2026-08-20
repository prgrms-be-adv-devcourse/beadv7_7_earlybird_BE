package com.growmighty.lectures.firstday.project.reward.presentation.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * PATCH /api/v1/rewards/{rewardId}.
 * 프로젝트 공개 전: name/description/price/totalQuantity 자유 수정.
 * totalQuantity는 null이면 "미변경"이라 유한→무제한으로 되돌릴 방법이 없다 — 그 경우엔
 * clearTotalQuantity=true로 보낸다(totalQuantity와 동시 지정 불가).
 * 프로젝트 공개 후: increaseQuantity(추가량)만 허용 — 그 외 필드를 같이 보내면 400.
 */
public record RewardUpdateRequest(
        String name,
        String description,
        BigDecimal price,
        @PositiveOrZero Integer totalQuantity,
        Boolean clearTotalQuantity,
        @Positive Integer increaseQuantity
) {
    public Boolean clearTotalQuantity() {
        return Boolean.TRUE.equals(clearTotalQuantity);
    }
}
