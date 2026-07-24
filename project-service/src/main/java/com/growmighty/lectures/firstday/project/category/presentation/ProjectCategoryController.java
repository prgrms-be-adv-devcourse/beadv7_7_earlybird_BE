package com.growmighty.lectures.firstday.project.category.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/project-categories")
public class ProjectCategoryController {

    private final ProjectCategoryService projectCategoryService;

    /** 관리자 전용 — 전역 taxonomy라 아무나 만들면 다른 모든 사용자의 목록/트리에 영향을 준다. */
    @PostMapping
    public ProjectCategoryResponse create(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                           @Valid @RequestBody ProjectCategoryCreateRequest request) {
        requireAdmin(requesterRole);
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

    /**
     * 관리자 전용 — 이름 변경 / 상위 카테고리 변경 (자기 자신·자손을 부모로 설정하면 거부됨).
     * 전역 taxonomy라 한 명이 바꾸면 다른 모든 사용자에게 영향을 준다.
     */
    @PutMapping("/{projectCategoryId}")
    public ProjectCategoryResponse update(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                           @PathVariable Long projectCategoryId,
                                           @Valid @RequestBody ProjectCategoryUpdateRequest request) {
        requireAdmin(requesterRole);
        return projectCategoryService.update(projectCategoryId, request);
    }

    private void requireAdmin(UserRole requesterRole) {
        if (requesterRole != UserRole.ADMIN) {
            throw new IllegalArgumentException("관리자만 접근할 수 있습니다.");
        }
    }
}
