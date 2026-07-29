package com.growmighty.lectures.firstday.project.project.application;

import java.math.BigDecimal;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectCreatorResponse;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;

import java.util.List;
import java.util.Optional;

public interface ProjectService {

    ProjectResponse create(Long creatorId, ProjectCreateRequest request);

    /** requesterRole이 ADMIN이면 PENDING_REVIEW/REJECTED도 결과에 포함한다. */
    List<ProjectResponse> findAll(String keyword, Long categoryId, ProjectStatus status, ProjectSort sort, UserRole requesterRole);

    ProjectResponse findById(Long projectId);

    /** requesterId가 본인이 등록한 프로젝트가 아니면 거부한다. */
    ProjectResponse update(Long projectId, Long requesterId, ProjectUpdateRequest request);

    /** requesterId가 본인이 등록한 프로젝트가 아니면 거부한다. */
    void delete(Long projectId, Long requesterId);

    /** 창작자(본인) 또는 관리자: 진행중이거나 이미 성공한 프로젝트를 자진 취소한다. */
    ProjectResponse cancel(Long projectId, Long requesterId, UserRole requesterRole);

    List<ProjectResponse> findByCreator(Long creatorId);

    /**
     * 서비스 간 내부 API(ProjectInternalController) 전용 — role 개념이 없는 호출자(Settlement/Payment)를
     * 위해 findAll()과 별개로 남겨둔다. findAll()의 role 기반 가시성 분기와 섞으면 내부 호출자에게
     * 억지로 role을 부여해야 해서 오히려 부자연스럽다.
     */
    List<ProjectResponse> findByStatus(ProjectStatus status);

    /**
     * 서비스 간 내부 API 전용 — board-service가 리뷰 생성 알림 메일을 보낼 대상(제작자)을 조회할 때 쓴다.
     * 존재하지 않는 projectId는 EntityNotFoundException(404)으로 처리한다.
     */
    ProjectCreatorResponse getCreator(Long projectId);

    // ── 관리자 ──────────────────────────────────────────────
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

    /**
     * 내부용: order-service에서 pull 조회해온 절대값(누적 총액)으로 한 프로젝트의 fundedAmount를
     * 덮어쓴다. 멱등. FundedAmountReconciliationScheduler가 프로젝트 하나씩 재시도 가능하도록
     * 호출하는 단위 — 외부에서 직접 부를 일은 없다.
     */
    void updateFundedAmount(Long projectId, BigDecimal fundedAmount);

    /** 배치 전용: IN_PROGRESS 프로젝트마다 order-service의 현재 확정 누적 총액을 pull해 보정한다. */
    void reconcileFundedAmounts();

    // ── reward 도메인이 호출하는 API (project-service 내부, 도메인 간) ──────
    /**
     * 지금 이 순간의 진짜 상태(공유락)를 뷰로 노출한다 — Reward가 Project 엔티티/리포지토리를
     * 직접 알 필요 없이 이 포트만으로 published/closed/open 여부를 판단할 수 있게 한다.
     */
    Optional<ProjectStatusView> findStatusView(Long projectId);
}
