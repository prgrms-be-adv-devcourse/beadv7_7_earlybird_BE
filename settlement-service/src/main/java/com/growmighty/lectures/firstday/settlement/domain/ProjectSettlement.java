package com.growmighty.lectures.firstday.settlement.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "project_settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_settlement_project_id", columnNames = "project_id")
)
public class ProjectSettlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Column(name = "calculation_policy_version", nullable = false, updatable = false, length = 100)
    private String calculationPolicyVersion;

    @Embedded
    private SettlementBreakdown breakdown;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "creatorId", column = @Column(name = "destination_creator_id", nullable = false, updatable = false)),
            @AttributeOverride(name = "tossSellerId", column = @Column(name = "destination_toss_seller_id", nullable = false, updatable = false, length = 100)),
            @AttributeOverride(name = "bankCode", column = @Column(name = "destination_bank_code", nullable = false, updatable = false, length = 20)),
            @AttributeOverride(name = "maskedAccountNumber", column = @Column(name = "destination_masked_account_number", nullable = false, updatable = false, length = 100))
    })
    private PayoutDestinationSnapshot destinationSnapshot;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    protected ProjectSettlement() {
    }

    private ProjectSettlement(
            Long projectId,
            Long creatorId,
            String calculationPolicyVersion,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDateTime confirmedAt
    ) {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        if (calculationPolicyVersion == null || calculationPolicyVersion.isBlank()) {
            throw new IllegalArgumentException("계산 정책 버전은 필수입니다.");
        }
        this.projectId = projectId;
        this.creatorId = creatorId;
        this.calculationPolicyVersion = calculationPolicyVersion;
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
            String calculationPolicyVersion,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDateTime confirmedAt
    ) {
        return new ProjectSettlement(
                projectId,
                creatorId,
                calculationPolicyVersion,
                breakdown,
                destinationSnapshot,
                confirmedAt
        );
    }

    public Money creatorPayoutAmount() {
        return breakdown.creatorPayoutAmount();
    }

    public PayoutDestinationSnapshot destinationSnapshot() {
        return destinationSnapshot;
    }
}
