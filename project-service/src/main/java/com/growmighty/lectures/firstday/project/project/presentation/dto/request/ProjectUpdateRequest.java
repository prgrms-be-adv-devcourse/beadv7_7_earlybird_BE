package com.growmighty.lectures.firstday.project.project.presentation.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PATCH /api/v1/projects/{projectId}.
 * 공개 전: 모든 필드 수정 가능. 공개 후: summary/description/thumbnailId만 허용.
 * endAt은 공개 후 창작자가 변경할 수 없다 (관리자 전용 — ProjectAdminController의 deadline API 참고).
 * null인 필드는 변경하지 않는다.
 */
public record ProjectUpdateRequest(
        String title,
        Long categoryId,
        String summary,
        String description,
        Long thumbnailId,
        BigDecimal goalAmount,
        LocalDateTime startAt,
        LocalDate endAt
) {
    public boolean hasPublishOnlyRestrictedField() {
        return title != null || categoryId != null || goalAmount != null || startAt != null || endAt != null;
    }
}
