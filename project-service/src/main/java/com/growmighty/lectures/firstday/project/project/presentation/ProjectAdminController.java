package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 심사 API.
 * TODO(팀): 관리자 권한 검증은 인증 도입 후 — Gateway 라우팅에 /admin 규칙 추가 필요
 * TODO(팀): JWT 도입만으로는 해결 안 됨 — 로그인 여부와 별개로 "이 사용자가 ADMIN role인가"를
 *           검증하는 role 체크 로직을 이 컨트롤러/서비스에 별도로 추가해야 한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/projects")
public class ProjectAdminController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<List<ProjectResponse>> findByStatus(@RequestParam ProjectStatus status) {
        return ApiResponse.ok(projectService.findByStatus(status));
    }

    /** 심사 승인 (PENDING_REVIEW → IN_PROGRESS) */
    @PostMapping("/{projectId}/approve")
    public ApiResponse<ProjectResponse> approve(@PathVariable Long projectId) {
        return ApiResponse.ok(projectService.approve(projectId));
    }

    /** 심사 반려 (PENDING_REVIEW → REJECTED) */
    @PostMapping("/{projectId}/reject")
    public ApiResponse<ProjectResponse> reject(@PathVariable Long projectId, @Valid @RequestBody ProjectRejectRequest request) {
        return ApiResponse.ok(projectService.reject(projectId, request));
    }

    /** 마감일 연장 (기존 값보다 뒤로만 가능) — 창작자는 endAt을 직접 바꿀 수 없다, 관리자 전용 */
    @PatchMapping("/{projectId}/deadline")
    public ApiResponse<ProjectResponse> extendDeadline(@PathVariable Long projectId,
                                                        @Valid @RequestBody ProjectDeadlineExtendRequest request) {
        return ApiResponse.ok(projectService.extendDeadline(projectId, request));
    }

    /**
     * 마감일 지난 진행중 프로젝트 일괄 성공/실패 확정을 수동으로 즉시 실행한다.
     * 매일 자정 스케줄러가 자동으로 돌지만(ProjectDeadlineScheduler), 자정까지 기다리지 않고
     * 테스트/운영 확인용으로 즉시 트리거하고 싶을 때 사용.
     */
    @PostMapping("/close-expired")
    public ApiResponse<Void> closeExpiredProjects() {
        projectService.closeExpiredProjects();
        return ApiResponse.ok(null);
    }

    /** 목표 금액을 이미 달성한 진행중 프로젝트를 마감일 전에 관리자가 조기 종료(성공 확정)한다. */
    @PostMapping("/{projectId}/close-early")
    public ApiResponse<ProjectResponse> closeEarly(@PathVariable Long projectId) {
        return ApiResponse.ok(projectService.closeEarly(projectId));
    }
}
