package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;

import java.util.List;
import java.util.Optional;

public interface ProjectService {

    ProjectResponse create(ProjectCreateRequest request);

    List<ProjectResponse> findAll(String keyword, Long categoryId, ProjectStatus status, ProjectSort sort);

    ProjectResponse findById(Long projectId);

    ProjectResponse update(Long projectId, ProjectUpdateRequest request);

    void delete(Long projectId);

    List<ProjectResponse> findByCreator(Long creatorId);

    // ── 관리자 ──────────────────────────────────────────────
    List<ProjectResponse> findByStatus(ProjectStatus status);

    ProjectResponse approve(Long projectId);

    ProjectResponse reject(Long projectId, ProjectRejectRequest request);

    /** 마감일 연장 (기존 값보다 뒤로만) */
    ProjectResponse extendDeadline(Long projectId, ProjectDeadlineExtendRequest request);

    /** 관리자 전용: 목표 금액을 이미 달성한 프로젝트를 마감일 전에 조기 종료(성공 확정)한다. */
    ProjectResponse closeEarly(Long projectId);

    /** 배치 전용: 마감시각이 지난 진행중 프로젝트를 모금액 기준으로 일괄 성공/실패 확정한다. */
    void closeExpiredProjects();

    /** closeExpiredProjects()가 프로젝트 하나씩 재시도 가능하도록 호출하는 단위. 외부에서 직접 부를 일은 없다. */
    void closeProjectByDeadline(Long projectId);

    // ── reward 도메인이 호출하는 API (project-service 내부, 도메인 간) ──────
    /**
     * 지금 이 순간의 진짜 상태(공유락)를 뷰로 노출한다 — Reward가 Project 엔티티/리포지토리를
     * 직접 알 필요 없이 이 포트만으로 published/closed/open 여부를 판단할 수 있게 한다.
     */
    Optional<ProjectStatusView> findStatusView(Long projectId);
}
