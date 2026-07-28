package com.growmighty.lectures.firstday.settlement.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class ProjectSettlement {

    private final Long id;
    private final Long projectId;
    private final Long creatorId;
    private final SettlementBreakdown breakdown;
    private final PayoutDestinationSnapshot destinationSnapshot;
    private final LocalDateTime confirmedAt;

    private ProjectSettlement(
            Long id,
            Long projectId,
            Long creatorId,
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
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        this.id = id;
        this.projectId = projectId;
        this.creatorId = creatorId;
        this.breakdown = Objects.requireNonNull(breakdown, "정산 금액 명세는 필수입니다.");
        this.destinationSnapshot = Objects.requireNonNull(destinationSnapshot, "지급 대상 스냅샷은 필수입니다.");
        if (!destinationSnapshot.belongsTo(creatorId)) {
            throw new IllegalArgumentException("프로젝트 창작자와 지급 대상 창작자가 일치해야 합니다.");
        }
        this.confirmedAt = Objects.requireNonNull(confirmedAt, "정산 확정 시각은 필수입니다.");
    }

    public static ProjectSettlement confirm(
            Long projectId,
            Long creatorId,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDateTime confirmedAt
    ) {
        return new ProjectSettlement(
                null,
                projectId,
                creatorId,
                breakdown,
                destinationSnapshot,
                confirmedAt
        );
    }

    public static ProjectSettlement restore(
            Long id,
            Long projectId,
            Long creatorId,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDateTime confirmedAt
    ) {
        return new ProjectSettlement(
                Objects.requireNonNull(id, "프로젝트 정산 식별자는 필수입니다."),
                projectId,
                creatorId,
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

    public Long creatorId() {
        return creatorId;
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
