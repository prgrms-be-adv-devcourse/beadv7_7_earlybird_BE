package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 서비스 간 내부 API — Settlement가 정산 대상(SUCCEEDED)과 환불 대상(FAILED/CANCELLED) 프로젝트
 * 목록을 모두 조회할 때 호출한다. Payment는 프로젝트 상태 조회 없이 orderId 기준 단건 PG 취소·환불만
 * 담당하므로 이 API를 호출하지 않는다.
 * Gateway를 거치지 않는 내부망 전용 경로다 — 팀 컨벤션에 따라 /internal/v1 프리픽스를 사용한다.
 * 관리자용 GET /api/v1/admin/projects?status=X 와 기능은 같지만, 그건 사람(ADMIN JWT) 전용이라
 * 서비스 간 호출에는 이 경로를 따로 둔다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/projects")
public class ProjectInternalController {

    private final ProjectService projectService;

    @GetMapping
    public List<ProjectResponse> findByStatus(@RequestParam ProjectStatus status) {
        return projectService.findByStatus(status);
    }
}
