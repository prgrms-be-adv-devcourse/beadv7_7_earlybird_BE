package com.growmighty.lectures.firstday.project.category.presentation.dto.response;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;

import java.util.List;

/** findAllAsTree() 전용 트리 노드 응답 — 단건 조회에서는 하위 트리를 구성하지 않으므로 쓰지 않는다. */
public record ProjectCategoryTreeResponse(
        Long id,
        Long parentProjectCategoryId,
        String name,
        List<ProjectCategoryTreeResponse> children
) {
    public static ProjectCategoryTreeResponse of(ProjectCategory projectCategory, List<ProjectCategoryTreeResponse> children) {
        return new ProjectCategoryTreeResponse(
                projectCategory.getId(), projectCategory.getParentProjectCategoryId(), projectCategory.getName(), children);
    }
}
