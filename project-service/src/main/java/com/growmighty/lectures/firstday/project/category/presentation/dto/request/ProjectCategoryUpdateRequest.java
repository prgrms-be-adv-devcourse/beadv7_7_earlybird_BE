package com.growmighty.lectures.firstday.project.category.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProjectCategoryUpdateRequest(
        Long parentProjectCategoryId,
        @NotBlank String name
) {
}
