package com.growmighty.lectures.firstday.project.category.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryUpdateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import com.growmighty.lectures.firstday.project.category.application.ProjectCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/project-categories")
public class ProjectCategoryController {

    private final ProjectCategoryService projectCategoryService;

    @PostMapping
    public ApiResponse<ProjectCategoryResponse> create(@Valid @RequestBody ProjectCategoryCreateRequest request) {
        return ApiResponse.ok(projectCategoryService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ProjectCategoryResponse>> findAll() {
        return ApiResponse.ok(projectCategoryService.findAllAsTree());
    }

    @GetMapping("/{projectCategoryId}")
    public ApiResponse<ProjectCategoryResponse> findById(@PathVariable Long projectCategoryId) {
        return ApiResponse.ok(projectCategoryService.findById(projectCategoryId));
    }

    /** 이름 변경 / 상위 카테고리 변경 (자기 자신·자손을 부모로 설정하면 거부됨) */
    @PutMapping("/{projectCategoryId}")
    public ApiResponse<ProjectCategoryResponse> update(@PathVariable Long projectCategoryId,
                                                @Valid @RequestBody ProjectCategoryUpdateRequest request) {
        return ApiResponse.ok(projectCategoryService.update(projectCategoryId, request));
    }
}
