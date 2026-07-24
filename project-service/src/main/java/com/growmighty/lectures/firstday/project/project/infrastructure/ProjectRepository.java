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
}
