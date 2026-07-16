package com.growmighty.lectures.firstday.project.category.application;

import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryUpdateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;

import java.util.List;

public interface ProjectCategoryService {

    ProjectCategoryResponse create(ProjectCategoryCreateRequest request);

    /** 전체 카테고리를 트리 구조로 반환한다. */
    List<ProjectCategoryResponse> findAllAsTree();

    ProjectCategoryResponse findById(Long projectCategoryId);

    ProjectCategoryResponse update(Long projectCategoryId, ProjectCategoryUpdateRequest request);

    void delete(Long projectCategoryId);
}
