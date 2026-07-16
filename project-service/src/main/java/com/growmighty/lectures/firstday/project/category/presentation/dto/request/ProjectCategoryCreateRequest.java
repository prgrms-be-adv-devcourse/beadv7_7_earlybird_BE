package com.growmighty.lectures.firstday.project.category.presentation.dto.request;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import jakarta.validation.constraints.NotBlank;

public record ProjectCategoryCreateRequest(
        Long parentProjectCategoryId,
        @NotBlank String name
) {
    public ProjectCategory toEntity() {
        return ProjectCategory.create(parentProjectCategoryId, name);
    }
}
