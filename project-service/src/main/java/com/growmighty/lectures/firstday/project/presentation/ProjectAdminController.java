package com.growmighty.lectures.firstday.project.presentation;

import com.growmighty.lectures.firstday.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.presentation.dto.ProjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 심사 API (API 명세서 §2.1).
 * TODO(팀): 관리자 권한 검증(🛡)은 인증 도입 후 — Gateway 라우팅에 /admin 규칙 추가 필요
 * TODO(팀): 반려 사유(body: { "reason": ... }) 저장은 도메인에 반려 사유 필드 추가 후
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/projects")
public class ProjectAdminController {
    private final ProjectService projectService;

    /** 심사 승인 (IN_REVIEW → OPEN) */
    @PostMapping("/{projectId}/approve")
    public ProjectResponse approve(@PathVariable Long projectId) {
        return ProjectResponse.from(projectService.approve(projectId));
    }

    /** 심사 반려 (IN_REVIEW → REJECTED) */
    @PostMapping("/{projectId}/reject")
    public ProjectResponse reject(@PathVariable Long projectId) {
        return ProjectResponse.from(projectService.reject(projectId));
    }
}
