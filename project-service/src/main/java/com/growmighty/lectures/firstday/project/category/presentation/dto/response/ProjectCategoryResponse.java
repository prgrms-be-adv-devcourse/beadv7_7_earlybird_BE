package com.growmighty.lectures.firstday.project.category.presentation.dto.response;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;

/** 단건 조회/생성/수정 응답. 트리 구조가 필요한 목록 조회는 ProjectCategoryTreeResponse를 쓴다. */
public record ProjectCategoryResponse(
        Long id,
        Long parentProjectCategoryId,
        String name
) {
    public static ProjectCategoryResponse from(ProjectCategory projectCategory) {
        return new ProjectCategoryResponse(
                projectCategory.getId(), projectCategory.getParentProjectCategoryId(), projectCategory.getName());
    }
}
