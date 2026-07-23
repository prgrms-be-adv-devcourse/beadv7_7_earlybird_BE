package com.growmighty.lectures.firstday.project.project.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** PATCH /api/v1/projects/{projectId}/deadline (관리자 전용). 기존 endAt보다 뒤로만 연장할 수 있다. */
public record ProjectDeadlineExtendRequest(
        @NotNull LocalDate endAt
) {
}
