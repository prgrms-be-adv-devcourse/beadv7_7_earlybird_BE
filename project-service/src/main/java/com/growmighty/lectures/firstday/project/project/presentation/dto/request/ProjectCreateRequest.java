package com.growmighty.lectures.firstday.project.project.presentation.dto.request;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectCreateRequest(
        Long thumbnailId,
        @NotBlank String title,
        @NotNull Long categoryId,
        String summary,
        String description,
        @NotNull BigDecimal goalAmount,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDate endAt
) {
    /** creatorId는 body가 아니라 게이트웨이가 JWT에서 채워주는 X-User-Id 헤더에서 받는다. */
    public Project toEntity(Long creatorId) {
        return Project.register(creatorId, thumbnailId, title, categoryId, summary, description,
                goalAmount, startAt, endAt);
    }
}
