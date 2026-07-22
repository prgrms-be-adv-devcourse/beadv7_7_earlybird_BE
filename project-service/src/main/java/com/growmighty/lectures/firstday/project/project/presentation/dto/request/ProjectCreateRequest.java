package com.growmighty.lectures.firstday.project.project.presentation.dto.request;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectCreateRequest(
        @NotNull Long creatorId,
        Long thumbnailId,
        @NotBlank String title,
        @NotNull Long categoryId,
        String summary,
        String description,
        @NotNull BigDecimal goalAmount,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDate endAt
) {
    public Project toEntity() {
        return Project.register(creatorId, thumbnailId, title, categoryId, summary, description,
                goalAmount, startAt, endAt);
    }
}
