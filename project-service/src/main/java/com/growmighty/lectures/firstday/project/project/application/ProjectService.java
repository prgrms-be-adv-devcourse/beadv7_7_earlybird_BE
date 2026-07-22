package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;

import java.util.List;

public interface ProjectService {

    ProjectResponse create(ProjectCreateRequest request);

    List<ProjectResponse> findAll(String keyword, Long categoryId, ProjectStatus status, ProjectSort sort);

    ProjectResponse findById(Long projectId);

    ProjectResponse update(Long projectId, ProjectUpdateRequest request);

    void delete(Long projectId);

    /** 창작자(본인) 또는 관리자: 진행중이거나 이미 성공한 프로젝트를 자진 취소한다. */
    ProjectResponse cancel(Long projectId, Long requesterId, UserRole requesterRole);

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
}
