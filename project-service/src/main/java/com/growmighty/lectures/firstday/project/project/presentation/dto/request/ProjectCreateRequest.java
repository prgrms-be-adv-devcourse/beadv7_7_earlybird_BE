package com.growmighty.lectures.firstday.project.project.presentation.dto.request;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectCreateRequest(
        Long thumbnailId,
        @NotBlank String title,
        @NotNull Long categoryId,
        String summary,
        String description,
        @NotNull BigDecimal goalAmount,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDate endAt,
        // 클라이언트가 이 생성 시도(재시도 포함) 동안 고정해서 재전송하는 키 — 파일 업로드 실패 후
        // 재클릭해도 같은 키면 중복 생성되지 않는다(order-service의 orderIdempotencyKey와 동일 계약).
        // 미전송 클라이언트와의 하위 호환을 위해 필수는 아니다 — 미전송 시 재시도 중복 방지는
        // 적용되지 않는다(기존 동작과 동일).
        UUID idempotencyKey
) {
    /** creatorId는 body가 아니라 게이트웨이가 JWT에서 채워주는 X-User-Id 헤더에서 받는다. */
    public Project toEntity(Long creatorId) {
        UUID key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID();
        return Project.register(creatorId, key, thumbnailId, title, categoryId, summary, description,
                goalAmount, startAt, endAt);
    }
}
