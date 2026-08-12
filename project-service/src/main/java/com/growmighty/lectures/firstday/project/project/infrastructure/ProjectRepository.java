package com.growmighty.lectures.firstday.project.project.infrastructure;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    List<Project> findByCreatorId(Long creatorId);

    /** ES 장애 시 하이브리드 검색의 DB LIKE 폴백 전용(ProjectSearchAdapter.searchFallback). */
    List<Project> findByTitleContainingIgnoreCase(String title);

    /** 카테고리 삭제 전 참조무결성 체크(ProjectCategoryServiceImpl.delete()) 전용. */
    boolean existsByCategoryId(Long categoryId);

    /** ProjectInternalController(서비스 간 내부 API) 전용 — role 없이 상태만으로 조회. */
    List<Project> findByStatus(ProjectStatus status);

    /**
     * 배치용: 아직 진행중인데 마감일(날짜)이 지난 프로젝트 조회. endAt 당일은 하루 종일 열려있는
     * 게 맞아서(2026-07-22 결정), "오늘"이 아니라 "오늘보다 이전"만 대상으로 한다 — endAt=오늘인
     * 프로젝트는 다음날 이 배치가 돌 때 비로소 대상이 된다.
     */
    List<Project> findByStatusAndEndAtLessThan(ProjectStatus status, LocalDate endAt);

    /**
     * 공유 락(LOCK IN SHARE MODE)으로 조회 — 트랜잭션의 REPEATABLE READ 스냅샷을 우회해 항상
     * 최신 커밋 상태를 읽고, 조회 중엔 다른 트랜잭션의 상태 변경(approve/reject 등)을 이 트랜잭션이
     * 끝날 때까지 대기시킨다. isPublished() 판단처럼 "지금 이 순간의 진짜 상태"가 중요한 곳에서만 사용.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Project p where p.projectId = :projectId")
    Optional<Project> findByIdForStatusCheck(@Param("projectId") Long projectId);

    /**
     * 배타 락(FOR UPDATE)으로 조회 — ProjectServiceImpl.delete() 전용.
     * Reward 쓰기 경로(RewardServiceImpl)는 findByIdForStatusCheck()로 Project를 공유 락으로
     * 먼저 잠그고 Reward를 나중에(커밋 시점) 잠근다. delete()는 반대로 Reward를 먼저 지우고
     * Project를 나중에 지우는 순서라, 동시에 겹치면 락 순서가 반대라서 데드락이 생길 수 있었다.
     * delete()가 맨 처음에 Project를 배타 락으로 선점하면, 그 사이 다른 트랜잭션은 공유 락도
     * 못 걸고 대기하게 돼(끼어들 틈이 없어져) 이 경합 자체가 발생하지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.projectId = :projectId")
    Optional<Project> findByIdForDelete(@Param("projectId") Long projectId);
}

