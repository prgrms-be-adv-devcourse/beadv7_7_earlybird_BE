package com.growmighty.lectures.firstday.project.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 프로젝트 = 펀딩 단위 (All-or-Nothing).
 * 가격·재고는 프로젝트가 아니라 {@link Reward}(후원 옵션)가 가진다.
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 창작자 = User 도메인의 userId (유저 단일 도메인 + role 구분) */
    @Column(nullable = false)
    private Long creatorId;

    /** projectdb.project_categories.id (논리) — 2차(소분류)만 지정, 필수값 */
    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String title;

    @Lob
    private String description;

    /** 목표 금액 — 마감 시 달성/실패 판정 기준 */
    @Column(nullable = false)
    private BigDecimal goalAmount;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    private Project(Long creatorId, Long categoryId, String title, String description,
                    BigDecimal goalAmount, LocalDateTime startAt, LocalDateTime endAt) {
        validateGoalAmount(goalAmount);
        validatePeriod(startAt, endAt);
        if (categoryId == null) {
            throw new IllegalArgumentException("카테고리는 필수입니다.");
        }
        this.creatorId = creatorId;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.goalAmount = goalAmount;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = ProjectStatus.DRAFT;
    }

    public static Project register(Long creatorId, Long categoryId, String title, String description,
                                   BigDecimal goalAmount, LocalDateTime startAt, LocalDateTime endAt) {
        return new Project(creatorId, categoryId, title, description, goalAmount, startAt, endAt);
    }

    // ── 상태 전이 ──────────────────────────────────────────────
    // TODO(팀): 전이 가드(어느 상태에서 어느 상태로만 갈 수 있는지) 규칙 확정 후 보강.
    //           현재는 흐름을 보여주는 최소 구현만 있다.

    /** 창작자: 작성 완료 → 심사 요청 */
    public void submitForReview() {
        requireStatus(ProjectStatus.DRAFT, "심사 요청은 작성중 상태에서만 가능합니다.");
        this.status = ProjectStatus.IN_REVIEW;
    }

    /** 관리자: 심사 승인 → 공개. TODO(팀): startAt 도래 전 승인 시 '공개 대기' 상태가 필요한지 논의 */
    public void approve() {
        requireStatus(ProjectStatus.IN_REVIEW, "승인은 심사중 상태에서만 가능합니다.");
        this.status = ProjectStatus.OPEN;
    }

    /** 관리자: 심사 반려. TODO(팀): 반려 사유 보관 위치(엔티티 필드 vs 별도 심사 이력) 결정 */
    public void reject() {
        requireStatus(ProjectStatus.IN_REVIEW, "반려는 심사중 상태에서만 가능합니다.");
        this.status = ProjectStatus.REJECTED;
    }

    /** 배치: 마감 처리. TODO(팀): 마감 배치(Spring Batch)에서 호출 — endAt 경과 검증 추가 */
    public void close() {
        requireStatus(ProjectStatus.OPEN, "마감은 공개 상태에서만 가능합니다.");
        this.status = ProjectStatus.CLOSED;
    }

    /** 배치: 목표 금액 달성 판정. TODO(팀): 총 후원액 집계 방식(Order 조회) 확정 */
    public void markGoalReached() {
        requireStatus(ProjectStatus.CLOSED, "달성 판정은 마감 상태에서만 가능합니다.");
        this.status = ProjectStatus.GOAL_REACHED;
    }

    /** 배치: 목표 금액 실패 판정 → 일괄 환불 대상 */
    public void markGoalFailed() {
        requireStatus(ProjectStatus.CLOSED, "실패 판정은 마감 상태에서만 가능합니다.");
        this.status = ProjectStatus.GOAL_FAILED;
    }

    /** 후원 가능 여부. TODO(팀): startAt/endAt 기간 검증 추가 (현재는 상태만 본다) */
    public boolean isOrderable() {
        return this.status == ProjectStatus.OPEN;
    }

    private void requireStatus(ProjectStatus expected, String message) {
        if (this.status != expected) {
            throw new IllegalStateException(message + " 현재 상태=" + this.status);
        }
    }

    private void validateGoalAmount(BigDecimal goalAmount) {
        if (goalAmount == null || goalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("목표 금액은 0원보다 커야 합니다. 입력값: " + goalAmount);
        }
    }

    private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("마감일은 시작일 이후여야 합니다. startAt=" + startAt + ", endAt=" + endAt);
        }
    }
}
