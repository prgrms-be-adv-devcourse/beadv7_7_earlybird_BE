package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
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

    /** ADMIN이면 심사 대기/반려 프로젝트도 함께 조회된다. */
    @GetMapping
    public ApiResponse<List<ProjectResponse>> findAll(
            @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) ProjectSort sort) {
        return ApiResponse.ok(projectService.findAll(keyword, categoryId, status, sort, requesterRole));
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

    /** 창작자(본인) 또는 관리자: 진행중이거나 이미 성공한 프로젝트를 자진 취소한다. */
    @PostMapping("/{projectId}/cancel")
    public ApiResponse<ProjectResponse> cancel(@PathVariable Long projectId,
                                                @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                                @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        return ApiResponse.ok(projectService.cancel(projectId, requesterId, requesterRole));
    }

    /** 심사 승인 (PENDING_REVIEW → IN_PROGRESS) */
    @PostMapping("/{projectId}/approve")
    public ApiResponse<ProjectResponse> approve(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                 @PathVariable Long projectId) {
        requireAdmin(requesterRole);
        return ApiResponse.ok(projectService.approve(projectId));
    }

    /** 심사 반려 (PENDING_REVIEW → REJECTED) */
    @PostMapping("/{projectId}/reject")
    public ApiResponse<ProjectResponse> reject(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                @PathVariable Long projectId, @Valid @RequestBody ProjectRejectRequest request) {
        requireAdmin(requesterRole);
        return ApiResponse.ok(projectService.reject(projectId, request));
    }

    /** 마감일 연장 (기존 값보다 뒤로만 가능) — 창작자는 endAt을 직접 바꿀 수 없다, 관리자 전용 */
    @PatchMapping("/{projectId}/deadline")
    public ApiResponse<ProjectResponse> extendDeadline(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                        @PathVariable Long projectId,
                                                        @Valid @RequestBody ProjectDeadlineExtendRequest request) {
        requireAdmin(requesterRole);
        return ApiResponse.ok(projectService.extendDeadline(projectId, request));
    }

    /**
     * 마감일 지난 진행중 프로젝트 일괄 성공/실패 확정을 수동으로 즉시 실행한다.
     * 매일 자정 스케줄러가 자동으로 돌지만(ProjectDeadlineScheduler), 자정까지 기다리지 않고
     * 테스트/운영 확인용으로 즉시 트리거하고 싶을 때 사용.
     */
    @PostMapping("/close-expired")
    public ApiResponse<Void> closeExpiredProjects(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        requireAdmin(requesterRole);
        projectService.closeExpiredProjects();
        return ApiResponse.ok(null);
    }

    /** 목표 금액을 이미 달성한 진행중 프로젝트를 마감일 전에 관리자가 조기 종료(성공 확정)한다. */
    @PostMapping("/{projectId}/close-early")
    public ApiResponse<ProjectResponse> closeEarly(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                    @PathVariable Long projectId) {
        requireAdmin(requesterRole);
        return ApiResponse.ok(projectService.closeEarly(projectId));
    }

    private void requireCreatorOrAdmin(UserRole requesterRole) {
        if (requesterRole != UserRole.CREATOR && requesterRole != UserRole.ADMIN) {
            throw new IllegalArgumentException("창작자 또는 관리자만 프로젝트를 등록할 수 있습니다.");
        }
    }

    private void requireAdmin(UserRole requesterRole) {
        if (requesterRole != UserRole.ADMIN) {
            throw new IllegalArgumentException("관리자만 접근할 수 있습니다.");
        }
    }
}
