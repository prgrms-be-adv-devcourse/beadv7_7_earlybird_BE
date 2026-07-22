package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 심사 API. 게이트웨이가 로그인 여부(인증)는 확인해주지만 role별 접근 제어(인가)는
 * 각 서비스 책임이라, 여기서 요청자의 role이 ADMIN인지 직접 검증한다
 * (board-service ProjectNotice.validateOwnership과 동일한 관례).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/projects")
public class ProjectAdminController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<List<ProjectResponse>> findByStatus(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                            @RequestParam ProjectStatus status) {
        requireAdmin(requesterRole);
        return ApiResponse.ok(projectService.findByStatus(status));
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

    private void requireAdmin(UserRole requesterRole) {
        if (requesterRole != UserRole.ADMIN) {
            throw new IllegalArgumentException("관리자만 접근할 수 있습니다.");
        }
    }
}
