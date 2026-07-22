package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    /** BACKER는 프로젝트를 등록할 수 없다 — CREATOR로 전환(users/me/creator)한 사용자 또는 ADMIN만 가능. */
    @PostMapping
    public ApiResponse<ProjectResponse> create(@RequestHeader(JwtHeaders.USER_ID) Long creatorId,
                                                @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                @Valid @RequestBody ProjectCreateRequest request) {
        requireCreatorOrAdmin(requesterRole);
        return ApiResponse.ok(projectService.create(creatorId, request));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> findAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) ProjectSort sort) {
        return ApiResponse.ok(projectService.findAll(keyword, categoryId, status, sort));
    }

    /** 내 프로젝트 목록. */
    @GetMapping("/me")
    public ApiResponse<List<ProjectResponse>> findMyProjects(@RequestHeader(JwtHeaders.USER_ID) Long userId) {
        return ApiResponse.ok(projectService.findByCreator(userId));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> findById(@PathVariable Long projectId) {
        return ApiResponse.ok(projectService.findById(projectId));
    }

    @PatchMapping("/{projectId}")
    public ApiResponse<ProjectResponse> update(@PathVariable Long projectId,
                                                @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                                @RequestBody ProjectUpdateRequest request) {
        return ApiResponse.ok(projectService.update(projectId, requesterId, request));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable Long projectId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        projectService.delete(projectId, requesterId);
        return ApiResponse.ok(null);
    }

    private void requireCreatorOrAdmin(UserRole requesterRole) {
        if (requesterRole != UserRole.CREATOR && requesterRole != UserRole.ADMIN) {
            throw new IllegalArgumentException("창작자 또는 관리자만 프로젝트를 등록할 수 있습니다.");
        }
    }
}
