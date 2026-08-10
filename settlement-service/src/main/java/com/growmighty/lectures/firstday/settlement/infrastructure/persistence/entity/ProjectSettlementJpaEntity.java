// TODO(settlement-plan): Keep confirmed amounts immutable in practice and preserve one settlement per project.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "project_settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_settlement_project_id", columnNames = "project_id")
)
public class ProjectSettlementJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Embedded
    private SettlementFeePolicySnapshotJpaEmbeddable feePolicySnapshot;

    @Embedded
    private SettlementBreakdownJpaEmbeddable breakdown;

    @Embedded
    private PayoutDestinationSnapshotJpaEmbeddable destinationSnapshot;

    @Column(name = "scheduled_date", nullable = false, updatable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PayoutStatus status;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    protected ProjectSettlementJpaEntity() {
    }

    private ProjectSettlementJpaEntity(ProjectSettlement settlement) {
        this.projectId = settlement.projectId();
        this.creatorId = settlement.creatorId();
        this.feePolicySnapshot = SettlementFeePolicySnapshotJpaEmbeddable.fromDomain(settlement);
        this.breakdown = SettlementBreakdownJpaEmbeddable.fromDomain(settlement);
        this.destinationSnapshot = PayoutDestinationSnapshotJpaEmbeddable.fromDomain(settlement);
        this.scheduledDate = settlement.scheduledDate();
        this.status = settlement.status();
        this.confirmedAt = settlement.confirmedAt();
    }

    public static ProjectSettlementJpaEntity fromDomain(ProjectSettlement settlement) {
        if (settlement.id() != null) {
            throw new IllegalArgumentException("이미 저장된 프로젝트 정산은 새 JPA 엔티티로 만들 수 없습니다.");
        }
        return new ProjectSettlementJpaEntity(settlement);
    }

    public ProjectSettlement toDomain(PayoutObligation payout) {
        if (!creatorId.equals(destinationSnapshot.creatorId())) {
            throw new IllegalArgumentException("프로젝트 창작자와 지급 대상 창작자가 일치해야 합니다.");
        }
        if (!id.equals(payout.settlementId())
                || !creatorId.equals(payout.creatorId())
                || !breakdown.creatorPayoutAmount().equals(payout.amount())
                || !scheduledDate.equals(payout.scheduledDate())) {
            throw new IllegalArgumentException("프로젝트 정산과 지급 상태가 일치하지 않습니다.");
        }
        return ProjectSettlement.restore(
                id,
                projectId,
                creatorId,
                feePolicySnapshot.paymentAndSettlementAgencyFeeRate(),
                feePolicySnapshot.platformFeeRate(),
                feePolicySnapshot.vatRate(),
                breakdown.baseAmount(),
                breakdown.paymentAndSettlementAgencyFeeAmount(),
                breakdown.paymentAndSettlementAgencyFeeVatAmount(),
                breakdown.platformFeeAmount(),
                breakdown.platformFeeVatAmount(),
                breakdown.otherDeductionAmount(),
                breakdown.creatorPayoutAmount(),
                destinationSnapshot.tossSellerId(),
                destinationSnapshot.bankCode(),
                destinationSnapshot.maskedAccountNumber(),
                scheduledDate,
                PayoutStatus.valueOf(payout.status().name()),
                payout.attempts(),
                payout.successfulAttemptSequence(),
                payout.version(),
                confirmedAt
        );
    }

    public Long id() {
        return id;
    }
}
