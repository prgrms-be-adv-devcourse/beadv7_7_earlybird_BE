package com.growmighty.lectures.firstday.board.feign.httpClient.project;

import com.growmighty.lectures.firstday.board.feign.httpClient.project.dto.ProjectCreatorApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
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

    // project-service 팀에 요청한 계약 — 이 엔드포인트는 project-service 쪽에 아직 구현돼 있지 않다 (board-service 몫만 먼저 작성).
    // 리뷰 생성 알림 메일 발송을 위해 projectId로 제작자 userId만 조회한다. (order-service 구매 검증 엔드포인트와 같은 방식의 선요청)
    @GetMapping("/internal/v1/projects/{projectId}/creator")
    ApiResponse<ProjectCreatorApiData> getCreator(@PathVariable("projectId") Long projectId);
}