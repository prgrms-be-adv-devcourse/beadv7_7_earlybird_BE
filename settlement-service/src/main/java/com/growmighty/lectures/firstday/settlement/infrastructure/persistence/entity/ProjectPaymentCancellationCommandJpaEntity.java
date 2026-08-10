// TODO(settlement-plan): Replace this per-order command entity with a project refund Outbox entity, then delete it.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPaymentCancellationCommand;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPaymentCancellationCommandStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Objects;

@Entity
@Table(
        name = "project_payment_cancellation_commands",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_payment_cancellation_order_id",
                        columnNames = "order_id"
                ),
                @UniqueConstraint(
                        name = "uk_project_payment_cancellation_idempotency_key",
                        columnNames = "idempotency_key"
                )
        },
        indexes = @Index(
                name = "idx_project_payment_cancellation_project_id",
                columnList = "project_id"
        )
)
public class ProjectPaymentCancellationCommandJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 50)
    private ProjectCancellationReason reason;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ProjectPaymentCancellationCommandStatus status;

    @Version
    private Long version;

    protected ProjectPaymentCancellationCommandJpaEntity() {
    }

    private ProjectPaymentCancellationCommandJpaEntity(
            ProjectPaymentCancellationCommand command
    ) {
        this.projectId = command.projectId();
        this.orderId = command.orderId();
        this.reason = command.reason();
        this.idempotencyKey = command.idempotencyKey();
        this.status = command.status();
    }

    public static ProjectPaymentCancellationCommandJpaEntity fromDomain(
            ProjectPaymentCancellationCommand command
    ) {
        if (command.id() != null || command.version() != null) {
            throw new IllegalArgumentException("이미 저장된 결제 취소 명령입니다.");
        }
        return new ProjectPaymentCancellationCommandJpaEntity(command);
    }

    public void sync(ProjectPaymentCancellationCommand command) {
        if (!Objects.equals(id, command.id())) {
            throw new IllegalArgumentException("결제 취소 명령 식별자는 변경할 수 없습니다.");
        }
        if (!Objects.equals(projectId, command.projectId())
                || !Objects.equals(orderId, command.orderId())
                || reason != command.reason()
                || !Objects.equals(idempotencyKey, command.idempotencyKey())) {
            throw new IllegalArgumentException("결제 취소 명령의 요청 원본은 변경할 수 없습니다.");
        }
        this.status = command.status();
    }

    public ProjectPaymentCancellationCommand toDomain() {
        return ProjectPaymentCancellationCommand.restore(
                id,
                projectId,
                orderId,
                reason,
                idempotencyKey,
                status,
                version
        );
    }

    public Long id() {
        return id;
    }

    public Long version() {
        return version;
    }
}
