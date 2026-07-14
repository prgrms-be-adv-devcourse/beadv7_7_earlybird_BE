package com.growmighty.lectures.firstday.project.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.presentation.dto.ChangeProjectPriceRequest;
import com.growmighty.lectures.firstday.project.presentation.dto.ChangeStockRequest;
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

    @PatchMapping("/{projectId}/price")
    public ApiResponse<ProjectResponse> changePrice(@PathVariable Long projectId, @RequestBody ChangeProjectPriceRequest request) {
        return ApiResponse.ok(ProjectResponse.from(projectService.changePrice(projectId, request.price())));
    }

    @PostMapping("/{projectId}/decrease-stock")
    public ApiResponse<Void> decreaseStock(@PathVariable Long projectId, @RequestBody ChangeStockRequest request) {
        projectService.decreaseStock(projectId, request.quantity());
        return ApiResponse.ok();
    }

    @PostMapping("/{projectId}/restore-stock")
    public ApiResponse<Void> restoreStock(@PathVariable Long projectId, @RequestBody ChangeStockRequest request) {
        projectService.restoreStock(projectId, request.quantity());
        return ApiResponse.ok();
    }

    @GetMapping("/banner")
    public Map<String, String> banner() {
        return Map.of("message", bannerMessage);
    }

}
