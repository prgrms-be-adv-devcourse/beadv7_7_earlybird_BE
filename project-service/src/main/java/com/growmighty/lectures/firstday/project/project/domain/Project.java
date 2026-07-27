package com.growmighty.lectures.firstday.project.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 프로젝트 = 펀딩 단위 (All-or-Nothing).
 * 가격·재고는 프로젝트가 아니라 Reward(후원 옵션)가 가진다.
 * 카테고리는 Category 도메인의 categoryId로만 참조한다 (같은 서비스, 별도 애그리거트).
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long projectId;

    @Column(nullable = false)
    private Long creatorId;

    private Long thumbnailId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Long categoryId;

    private String summary;

    @Lob
    private String description;

    @Column(nullable = false)
    private BigDecimal goalAmount;

    // updateFundedAmount()로 갱신된다 — order-service가 결제 확정/취소 시 push로 호출하거나
    // (PUT /internal/v1/projects/{id}/funded-amount), project-service가 주기적으로 pull(OrderPort)해
    // 검증한다. TODO(팀): order-service의 push 발신, pull 응답의 PAID 필터링은 아직 완료 안 됨.
    @Column(nullable = false)
    private BigDecimal fundedAmount;

    // 실제 펀딩 시작 날짜-시간
    @Column(nullable = false)
    private LocalDateTime startAt;

    // 프로젝트 기간이 일 단위라 마감도 일 단위로 끊는다 — 시간까지 받으면 배치가 "오늘 마감인 것"을
    // 정확히 못 걸러낸다(LocalDateTime이면 시각이 자정이 아닌 값도 들어올 수 있음). endAt 당일은
    // "마감일까지"라는 의미대로 하루 종일 포함되고, 실제 마감은 "그 다음날 00:00"에 일어난다
    // (isOpen()/closeExpiredProjects 배치 참고. 2026-07-22 리뷰에서 당일 00:00 마감이 "N일까지"라는
    // 표현과 어긋난다는 지적을 받아 다음날 00:00 마감으로 결정).
    @Column(nullable = false)
    private LocalDate endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    private String rejectReason;

    private LocalDateTime submittedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime closedAt;

    // 프로젝트 게시 날짜-시간
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 마감 배치(closeByDeadline)와 관리자 마감일 연장(extendDeadline)이 같은 행을 동시에 건드릴 수 있어 충돌 감지용 */
    @Version
    private Long version;

    private Project(Long creatorId, Long thumbnailId, String title, Long categoryId, String summary,
                     String description, BigDecimal goalAmount, LocalDateTime startAt, LocalDate endAt) {
        validateGoalAmount(goalAmount);
        validatePeriod(startAt, endAt);
        this.creatorId = creatorId;
        this.thumbnailId = thumbnailId;
        this.title = title;
        this.categoryId = categoryId;
        this.summary = summary;
        this.description = description;
        this.goalAmount = goalAmount;
        this.fundedAmount = BigDecimal.ZERO;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = ProjectStatus.PENDING_REVIEW;
        this.submittedAt = LocalDateTime.now();
    }

    public static Project register(Long creatorId, Long thumbnailId, String title, Long categoryId, String summary,
                                    String description, BigDecimal goalAmount, LocalDateTime startAt, LocalDate endAt) {
        return new Project(creatorId, thumbnailId, title, categoryId, summary, description, goalAmount, startAt, endAt);
    }

    /** 관리자: 심사 승인 (PENDING_REVIEW → IN_PROGRESS) */
    public void approve() {
        requireStatus(ProjectStatus.PENDING_REVIEW, "승인은 심사 대기 상태에서만 가능합니다.");
        this.status = ProjectStatus.IN_PROGRESS;
        this.approvedAt = LocalDateTime.now();
    }

    /** 관리자: 심사 반려 (PENDING_REVIEW → REJECTED) */
    public void reject(String reason) {
        requireStatus(ProjectStatus.PENDING_REVIEW, "반려는 심사 대기 상태에서만 가능합니다.");
        this.status = ProjectStatus.REJECTED;
        this.rejectReason = reason;
    }

    /** 승인을 거쳐 한 번이라도 공개된 적이 있는지 여부. 공개 후에는 수정 가능한 필드가 제한된다. */
    public boolean isPublished() {
        return this.status != ProjectStatus.PENDING_REVIEW && this.status != ProjectStatus.REJECTED;
    }

    public boolean isClosed() {
        return this.status.isClosed();
    }

    /**
     * 지금 이 순간 새 주문(리워드 구매)을 받아도 되는 상태인지 — 저장된 status만으로는 마감시각이
     * 지났는데 배치가 아직 안 돈 구간을 못 걸러낸다. endAt까지 실시간으로 같이 봐야 배치 주기와
     * 무관하게 마감 순간 즉시 주문이 막힌다(성공/실패 확정은 여전히 closeByDeadline 배치 몫).
     */
    public boolean isOpen() {
        return this.status == ProjectStatus.IN_PROGRESS
                && LocalDateTime.now().isBefore(this.endAt.plusDays(1).atStartOfDay());
    }

    /** 공개 전: 전체 필드 수정 가능 (null인 값은 변경하지 않음) */
    public void updateBeforePublish(String title, Long categoryId, String summary, String description,
                                     Long thumbnailId, BigDecimal goalAmount, LocalDateTime startAt, LocalDate endAt) {
        if (isPublished()) {
            throw new IllegalStateException("공개된 프로젝트는 이 방식으로 수정할 수 없습니다. 현재 상태=" + this.status);
        }
        if (title != null) {
            this.title = title;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (summary != null) {
            this.summary = summary;
        }
        if (description != null) {
            this.description = description;
        }
        if (thumbnailId != null) {
            this.thumbnailId = thumbnailId;
        }
        if (goalAmount != null) {
            validateGoalAmount(goalAmount);
            this.goalAmount = goalAmount;
        }
        if (startAt != null || endAt != null) {
            LocalDateTime newStartAt = startAt != null ? startAt : this.startAt;
            LocalDate newEndAt = endAt != null ? endAt : this.endAt;
            validatePeriod(newStartAt, newEndAt);
            this.startAt = newStartAt;
            this.endAt = newEndAt;
        }
    }

    /** 공개 후: summary/description/thumbnailId만 수정 가능. endAt은 창작자가 직접 바꿀 수 없다 (관리자 전용 — extendDeadline 참고). */
    public void updateAfterPublish(String summary, String description, Long thumbnailId) {
        if (summary != null) {
            this.summary = summary;
        }
        if (description != null) {
            this.description = description;
        }
        if (thumbnailId != null) {
            this.thumbnailId = thumbnailId;
        }
    }

    /** 관리자 전용: 마감일 연장만 허용 (과거로 당길 수 없음) */
    public void extendDeadline(LocalDate newEndAt) {
        if (newEndAt == null || !newEndAt.isAfter(this.endAt)) {
            throw new IllegalArgumentException("마감일은 현재 마감일 이후로만 연장할 수 있습니다. 현재 마감일=" + this.endAt + ", 요청값=" + newEndAt);
        }
        this.endAt = newEndAt;
    }

    /**
     * order-service가 알려주는(push) 또는 조회해온(pull) "현재 확정 총액"으로 덮어쓴다 — 증분이 아니라
     * 절대값이라 같은 값으로 여러 번 호출해도 결과가 같다(멱등).
     */
    public void updateFundedAmount(BigDecimal fundedAmount) {
        if (fundedAmount == null || fundedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("모금액은 0 이상이어야 합니다. fundedAmount=" + fundedAmount);
        }
        this.fundedAmount = fundedAmount;
    }

    /**
     * 배치 전용: 마감시각이 지난 진행중 프로젝트를 모금액 기준으로 성공/실패 확정한다.
     */
    public void closeByDeadline() {
        requireStatus(ProjectStatus.IN_PROGRESS, "마감 처리는 진행중 상태에서만 가능합니다.");
        this.status = this.fundedAmount.compareTo(this.goalAmount) >= 0
            ? ProjectStatus.SUCCEEDED
            : ProjectStatus.FAILED;
        this.closedAt = LocalDateTime.now();
    }

    /**
     * 관리자 전용: 이미 목표 금액을 달성한 프로젝트를 마감일 전에 조기 종료해 성공으로 확정한다
     * (재료 소진 등으로 더 이상 후원을 받고 싶지 않은 경우 — 크리에이터 요청을 받아 관리자가 처리).
     * 목표 미달 상태에서는 허용하지 않는다 — 그건 취소(CANCELLED, 전원 환불)로 처리할 문제지
     * 조기 마감이 아니다.
     */
    public void closeEarlyAsSucceeded() {
        requireStatus(ProjectStatus.IN_PROGRESS, "조기 마감은 진행중 상태에서만 가능합니다.");
        if (this.fundedAmount.compareTo(this.goalAmount) < 0) {
            throw new IllegalStateException(
                "목표 금액을 아직 달성하지 못해 조기 마감할 수 없습니다. 모금액=" + this.fundedAmount + ", 목표=" + this.goalAmount);
        }
        this.status = ProjectStatus.SUCCEEDED;
        this.closedAt = LocalDateTime.now();
    }

    /**
     * 창작자 또는 관리자: 자진 취소. 진행중이거나 이미 목표를 달성한 상태에서만 가능하다 — 실패(FAILED)는
     * 이미 자동으로 환불 파이프라인을 타므로 취소 대상이 아니고, 이미 취소된 것도 다시 취소할 수 없다.
     * 목표를 달성한(SUCCEEDED) 뒤의 취소는 "성공은 했지만 창작자가 배송 등을 감당 못 하게 된" 경우를
     * 위한 것으로, 정산(창작자 지급) 전까지만 허용한다 — Payment가 CANCELLED를 FAILED와 동일하게
     * 환불 대상으로 조회해간다(내부 상태별 조회 API 참고).
     */
    public void cancel() {
        if (this.status != ProjectStatus.IN_PROGRESS && this.status != ProjectStatus.SUCCEEDED) {
            throw new IllegalStateException("진행중이거나 이미 성공한 상태에서만 취소할 수 있습니다. 현재 상태=" + this.status);
        }
        this.status = ProjectStatus.CANCELLED;
        this.closedAt = LocalDateTime.now();
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

    private void validatePeriod(LocalDateTime startAt, LocalDate endAt) {
        if (startAt == null || endAt == null || !endAt.atStartOfDay().isAfter(startAt)) {
            throw new IllegalArgumentException("마감일은 시작일 이후여야 합니다. startAt=" + startAt + ", endAt=" + endAt);
        }
    }
}
