package com.growmighty.lectures.firstday.project.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.presentation.dto.ProjectResponse;
import com.growmighty.lectures.firstday.project.presentation.dto.RegisterProjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RefreshScope
@RestController
@RequiredArgsConstructor
@RequestMapping("/projects")
public class ProjectController {
    private final ProjectService projectService;

    @Value("${project.banner.message:이벤트 준비 중}")
    private String bannerMessage;

    @PostMapping
    public ApiResponse<ProjectResponse> register(@RequestBody RegisterProjectRequest request) {
        return ApiResponse.ok(ProjectResponse.from(projectService.register(request.toCommand())));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> getProject(@PathVariable Long projectId) {
        return ApiResponse.ok(ProjectResponse.from(projectService.getProjectInfo(projectId)));
    }

    /** 창작자: 심사 요청. TODO(팀): 인증 붙인 뒤 본인 프로젝트인지 검증 */
    @PostMapping("/{projectId}/submit")
    public ApiResponse<ProjectResponse> submitForReview(@PathVariable Long projectId) {
        return ApiResponse.ok(ProjectResponse.from(projectService.submitForReview(projectId)));
    }

    /** 관리자: 심사 승인. TODO(팀): Admin 컨텍스트 확정 후 관리자 권한 검증/경로 이동(/admin/**) 논의 */
    @PostMapping("/{projectId}/approve")
    public ApiResponse<ProjectResponse> approve(@PathVariable Long projectId) {
        return ApiResponse.ok(ProjectResponse.from(projectService.approve(projectId)));
    }

    /** 관리자: 심사 반려 */
    @PostMapping("/{projectId}/reject")
    public ApiResponse<ProjectResponse> reject(@PathVariable Long projectId) {
        return ApiResponse.ok(ProjectResponse.from(projectService.reject(projectId)));
    }

    @GetMapping("/banner")
    public Map<String, String> banner() {
        return Map.of("message", bannerMessage);
    }

}
