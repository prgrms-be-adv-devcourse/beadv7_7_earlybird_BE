package com.growmighty.lectures.firstday.board.feign.httpClient.project;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// 존재 여부만 필요해 응답 바디는 역직렬화하지 않는다 — 200이면 존재, 404(FeignException.NotFound)면 존재하지 않음.
// project-service의 GET /api/v1/projects/{projectId}는 미공개(PENDING_REVIEW/REJECTED) 상태도 404로 응답한다 —
// 아직 공개되지 않은 프로젝트엔 댓글도 달 수 없어야 하므로 이 부작용은 오히려 원하는 동작이다.
@FeignClient(name = "project-service")
public interface ProjectFeignClient {

    @GetMapping("/api/v1/projects/{projectId}")
    void getProject(@PathVariable("projectId") Long projectId);
}