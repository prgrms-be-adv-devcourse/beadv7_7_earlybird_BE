package com.growmighty.lectures.firstday.project.presentation;

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
    public ProjectResponse register(@RequestBody RegisterProjectRequest request) {
        return ProjectResponse.from(projectService.register(request.toCommand()));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable Long projectId) {
        return ProjectResponse.from(projectService.getProjectInfo(projectId));
    }

    /** 창작자: 심사 요청. TODO(팀): 인증 붙인 뒤 본인 프로젝트인지 검증 */
    @PostMapping("/{projectId}/submit")
    public ProjectResponse submitForReview(@PathVariable Long projectId) {
        return ProjectResponse.from(projectService.submitForReview(projectId));
    }

    @GetMapping("/banner")
    public Map<String, String> banner() {
        return Map.of("message", bannerMessage);
    }

}
