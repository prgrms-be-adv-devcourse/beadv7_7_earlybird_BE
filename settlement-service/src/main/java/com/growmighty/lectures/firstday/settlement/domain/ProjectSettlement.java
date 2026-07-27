package com.growmighty.lectures.firstday.settlement.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class ProjectSettlement {

    private final Long id;
    private final Long projectId;
    private final String projectTitle;
    private final Long creatorId;
    private final SettlementFeePolicySnapshot feePolicySnapshot;
    private final SettlementBreakdown breakdown;
    private final PayoutDestinationSnapshot destinationSnapshot;
    private final LocalDateTime confirmedAt;

    private ProjectSettlement(
            Long id,
            Long projectId,
            String projectTitle,
            Long creatorId,
            SettlementFeePolicySnapshot feePolicySnapshot,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDateTime confirmedAt
    ) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("프로젝트 정산 식별자는 양수여야 합니다.");
        }
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        if (projectTitle == null || projectTitle.isBlank()) {
            throw new IllegalArgumentException("프로젝트 제목은 필수입니다.");
        }
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        this.id = id;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.creatorId = creatorId;
        this.feePolicySnapshot = Objects.requireNonNull(feePolicySnapshot, "수수료 정책 스냅샷은 필수입니다.");
        this.breakdown = Objects.requireNonNull(breakdown, "정산 금액 명세는 필수입니다.");
        this.destinationSnapshot = Objects.requireNonNull(destinationSnapshot, "지급 대상 스냅샷은 필수입니다.");
        if (!destinationSnapshot.belongsTo(creatorId)) {
            throw new IllegalArgumentException("프로젝트 창작자와 지급 대상 창작자가 일치해야 합니다.");
        }
        this.confirmedAt = Objects.requireNonNull(confirmedAt, "정산 확정 시각은 필수입니다.");
    }

    public static ProjectSettlement confirm(
            Long projectId,
            String projectTitle,
            Long creatorId,
            SettlementFeePolicySnapshot feePolicySnapshot,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDateTime confirmedAt
    ) {
        return new ProjectSettlement(
                null,
                projectId,
                projectTitle,
                creatorId,
                feePolicySnapshot,
                breakdown,
                destinationSnapshot,
                confirmedAt
        );
    }

    public static ProjectSettlement restore(
            Long id,
            Long projectId,
            String projectTitle,
            Long creatorId,
            SettlementFeePolicySnapshot feePolicySnapshot,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDateTime confirmedAt
    ) {
        return new ProjectSettlement(
                Objects.requireNonNull(id, "프로젝트 정산 식별자는 필수입니다."),
                projectId,
                projectTitle,
                creatorId,
                feePolicySnapshot,
                breakdown,
                destinationSnapshot,
                confirmedAt
        );
    }

    public Long id() {
        return id;
    }

    public Long projectId() {
        return projectId;
    }

    public String projectTitle() {
        return projectTitle;
    }

    public Long creatorId() {
        return creatorId;
    }

    public SettlementFeePolicySnapshot feePolicySnapshot() {
        return feePolicySnapshot;
    }

    public SettlementBreakdown breakdown() {
        return breakdown;
    }

    public Money creatorPayoutAmount() {
        return breakdown.creatorPayoutAmount();
    }

    public PayoutDestinationSnapshot destinationSnapshot() {
        return destinationSnapshot;
    }

    public LocalDateTime confirmedAt() {
        return confirmedAt;
    }
}
