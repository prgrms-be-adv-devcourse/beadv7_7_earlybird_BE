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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
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

    @Column(nullable = false)
    private BigDecimal fundedAmount;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    private String rejectReason;

    private LocalDateTime submittedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime closedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Project(Long creatorId, Long thumbnailId, String title, Long categoryId, String summary,
                     String description, BigDecimal goalAmount, LocalDateTime startAt, LocalDateTime endAt) {
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
                                    String description, BigDecimal goalAmount, LocalDateTime startAt, LocalDateTime endAt) {
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

    /** 공개 전: 전체 필드 수정 가능 (null인 값은 변경하지 않음) */
    public void updateBeforePublish(String title, Long categoryId, String summary, String description,
                                     Long thumbnailId, BigDecimal goalAmount, LocalDateTime startAt, LocalDateTime endAt) {
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
            LocalDateTime newEndAt = endAt != null ? endAt : this.endAt;
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
    public void extendDeadline(LocalDateTime newEndAt) {
        if (newEndAt == null || !newEndAt.isAfter(this.endAt)) {
            throw new IllegalArgumentException("마감일은 현재 마감일 이후로만 연장할 수 있습니다. 현재 마감일=" + this.endAt + ", 요청값=" + newEndAt);
        }
        this.endAt = newEndAt;
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
