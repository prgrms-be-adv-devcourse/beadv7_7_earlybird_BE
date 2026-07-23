package com.growmighty.lectures.firstday.project.category.presentation;

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
    public ProjectCategoryResponse create(@Valid @RequestBody ProjectCategoryCreateRequest request) {
        return projectCategoryService.create(request);
    }

    @GetMapping
    public List<ProjectCategoryResponse> findAll() {
        return projectCategoryService.findAllAsTree();
    }

    @GetMapping("/{projectCategoryId}")
    public ProjectCategoryResponse findById(@PathVariable Long projectCategoryId) {
        return projectCategoryService.findById(projectCategoryId);
    }

    /** 이름 변경 / 상위 카테고리 변경 (자기 자신·자손을 부모로 설정하면 거부됨) */
    @PutMapping("/{projectCategoryId}")
    public ProjectCategoryResponse update(@PathVariable Long projectCategoryId,
                                           @Valid @RequestBody ProjectCategoryUpdateRequest request) {
        return projectCategoryService.update(projectCategoryId, request);
    }
}
