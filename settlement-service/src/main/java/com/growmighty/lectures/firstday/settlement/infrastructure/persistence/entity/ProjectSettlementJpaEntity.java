package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

    /**
     * 과거 스키마와의 호환을 위해서만 유지한다. 신규 정산은 Project 제목을 받거나 저장하지 않는다.
     */
    @Column(name = "project_title", updatable = false)
    private String legacyProjectTitle;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Embedded
    private SettlementFeePolicySnapshotJpaEmbeddable feePolicySnapshot;

    @Embedded
    private SettlementBreakdownJpaEmbeddable breakdown;

    @Embedded
    private PayoutDestinationSnapshotJpaEmbeddable destinationSnapshot;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    protected ProjectSettlementJpaEntity() {
    }

    private ProjectSettlementJpaEntity(ProjectSettlement settlement) {
        this.projectId = settlement.projectId();
        this.creatorId = settlement.creatorId();
        this.feePolicySnapshot = SettlementFeePolicySnapshotJpaEmbeddable.fromDomain(
                settlement.feePolicySnapshot()
        );
        this.breakdown = SettlementBreakdownJpaEmbeddable.fromDomain(settlement.breakdown());
        this.destinationSnapshot = PayoutDestinationSnapshotJpaEmbeddable.fromDomain(
                settlement.destinationSnapshot()
        );
        this.confirmedAt = settlement.confirmedAt();
    }

    public static ProjectSettlementJpaEntity fromDomain(ProjectSettlement settlement) {
        if (settlement.id() != null) {
            throw new IllegalArgumentException("이미 저장된 프로젝트 정산은 새 JPA 엔티티로 만들 수 없습니다.");
        }
        return new ProjectSettlementJpaEntity(settlement);
    }

    public ProjectSettlement toDomain() {
        return ProjectSettlement.restore(
                id,
                projectId,
                creatorId,
                feePolicySnapshot.toDomain(),
                breakdown.toDomain(),
                destinationSnapshot.toDomain(),
                confirmedAt
        );
    }

    public Long id() {
        return id;
    }
}
