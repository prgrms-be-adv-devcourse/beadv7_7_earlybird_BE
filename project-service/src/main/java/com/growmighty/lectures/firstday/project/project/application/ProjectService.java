package com.growmighty.lectures.firstday.project.project.application;

import java.math.BigDecimal;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
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

    /** title prefix 매치(자동완성). 매치 없으면 빈 리스트. ES 장애 시 ServiceUnavailableException. */
    List<ProjectSuggestion> autocomplete(String keyword);

    /** 비공개(PENDING_REVIEW/REJECTED) 프로젝트는 소유자 본인 또는 ADMIN만 조회할 수 있다. */
    ProjectResponse findById(Long projectId, Long requesterId, UserRole requesterRole);

    /** requesterId가 본인이 등록한 프로젝트가 아니면 거부한다. */
    ProjectResponse update(Long projectId, Long requesterId, ProjectUpdateRequest request);

    /** requesterId가 본인이 등록한 프로젝트가 아니면 거부한다. */
    void delete(Long projectId, Long requesterId);

    /**
     * delete()가 order-service 조회(orderPort.hasOrderedReward)를 트랜잭션 밖에서 끝낸 뒤 호출하는
     * 내부용 — 배타 락 선점 순서(락 순서 역전 데드락 방지, PR #79) 불변식을 지키기 위해
     * findByIdForDelete()로 Project를 맨 먼저 잠그고 Reward를 나중에 지운다. 외부에서 직접 부를 일은 없다.
     */
    void deleteInternal(Long projectId, Long requesterId);

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

    /**
     * closeEarly()가 order-service 조회(orderPort.getFundedAmount)를 트랜잭션 밖에서 끝낸 뒤,
     * 그 결과값만 들고 호출하는 내부용 — 외부에서 직접 부를 일은 없다.
     */
    ProjectResponse closeEarlyInternal(Long projectId, BigDecimal fundedAmount);

    /** 배치 전용: 마감시각이 지난 진행중 프로젝트를 모금액 기준으로 일괄 성공/실패 확정한다. */
    void closeExpiredProjects();

    /** closeExpiredProjects()가 프로젝트 하나씩 재시도 가능하도록 호출하는 단위. 외부에서 직접 부를 일은 없다. */
    void closeProjectByDeadline(Long projectId);

    /**
     * closeProjectByDeadline()이 order-service 조회(orderPort.getFundedAmount)를 트랜잭션 밖에서
     * 끝낸 뒤, 그 결과값만 들고 호출하는 내부용 — 외부에서 직접 부를 일은 없다.
     */
    void closeProjectByDeadlineInternal(Long projectId, BigDecimal fundedAmount);

    /**
     * 내부용: order-service에서 pull 조회해온 절대값(누적 총액)으로 한 프로젝트의 fundedAmount를
     * 덮어쓴다. 멱등. FundedAmountReconciliationScheduler가 프로젝트 하나씩 재시도 가능하도록
     * 호출하는 단위 — 외부에서 직접 부를 일은 없다.
     */
    void updateFundedAmount(Long projectId, BigDecimal fundedAmount);

    /** 배치 전용: IN_PROGRESS 프로젝트마다 order-service의 현재 확정 누적 총액을 pull해 보정한다. */
    void reconcileFundedAmounts();

    /** 관리자 전용: ES 검색 인덱스가 MySQL과 어긋났을 때 전체를 다시 색인한다(백필/복구). */
    void reindexAllProjects();

    // ── reward 도메인이 호출하는 API (project-service 내부, 도메인 간) ──────
    /**
     * 지금 이 순간의 진짜 상태(공유락)를 뷰로 노출한다 — Reward가 Project 엔티티/리포지토리를
     * 직접 알 필요 없이 이 포트만으로 published/closed/open 여부를 판단할 수 있게 한다.
     */
    Optional<ProjectStatusView> findStatusView(Long projectId);

    /**
     * ES 색인의 rewardNames는 project-service가 색인 시점에 리워드를 직접 다시 조회해 채운다 —
     * 리워드 이름이 바뀌면(등록/수정/삭제) 그 프로젝트를 재색인해야 검색에 반영된다. 프로젝트가
     * 이미 사라졌으면(고아 리워드) 조용히 무시한다.
     */
    void reindex(Long projectId);
}
