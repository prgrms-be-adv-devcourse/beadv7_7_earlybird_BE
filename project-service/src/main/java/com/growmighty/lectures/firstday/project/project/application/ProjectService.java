package com.growmighty.lectures.firstday.project.project.application;

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

    List<ProjectResponse> findByCreator(Long creatorId);

    // ── 관리자 ──────────────────────────────────────────────
    List<ProjectResponse> findByStatus(ProjectStatus status);

    ProjectResponse approve(Long projectId);

    ProjectResponse reject(Long projectId, ProjectRejectRequest request);

    /** 마감일 연장 (기존 값보다 뒤로만) */
    ProjectResponse extendDeadline(Long projectId, ProjectDeadlineExtendRequest request);
}
