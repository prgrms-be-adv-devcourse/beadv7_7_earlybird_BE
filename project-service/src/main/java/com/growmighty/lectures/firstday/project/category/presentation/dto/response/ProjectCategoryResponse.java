package com.growmighty.lectures.firstday.project.category.presentation.dto.response;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;

import java.util.List;

public record ProjectCategoryResponse(
        Long id,
        Long parentProjectCategoryId,
        String name,
        List<ProjectCategoryResponse> children
) {
    public static ProjectCategoryResponse of(ProjectCategory projectCategory, List<ProjectCategoryResponse> children) {
        return new ProjectCategoryResponse(projectCategory.getId(), projectCategory.getParentProjectCategoryId(), projectCategory.getName(), children);
    }

    public static ProjectCategoryResponse leaf(ProjectCategory projectCategory) {
        return of(projectCategory, List.of());
    }
}
