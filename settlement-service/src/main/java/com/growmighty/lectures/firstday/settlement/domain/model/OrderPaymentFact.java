package com.growmighty.lectures.firstday.settlement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "order_payment_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_payment_fact_pg_order_id",
                columnNames = "pg_order_id"
        ),
        indexes = @Index(name = "idx_order_payment_fact_project_id", columnList = "project_id")
)
public class OrderPaymentFact {

    public enum Status {
        COMPLETED,
        CANCELLED
    }

    public enum ReconciliationStatus {
        PENDING,
        REVIEW_REQUIRED,
        CONFIRMED
    }

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "pg_order_id", nullable = false, updatable = false, length = 100)
    private String pgOrderId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "payment_amount", nullable = false, updatable = false, precision = 19, scale = 0)
    private Money paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 20)
    private ReconciliationStatus reconciliationStatus;

    @Version
    private Long version;

    protected OrderPaymentFact() {
    }

    private OrderPaymentFact(
            Long orderId,
            String pgOrderId,
            Long projectId,
            Money paymentAmount,
            Instant completedAt
    ) {
        this.orderId = orderId;
        this.pgOrderId = pgOrderId;
        this.projectId = projectId;
        this.paymentAmount = paymentAmount;
        this.status = Status.COMPLETED;
        this.reconciliationStatus = ReconciliationStatus.PENDING;
        this.completedAt = completedAt;
        validateState();
    }

    public static OrderPaymentFact completed(
            Long orderId,
            String pgOrderId,
            Long projectId,
            Money paymentAmount,
            Instant completedAt
    ) {
        return new OrderPaymentFact(orderId, pgOrderId, projectId, paymentAmount, completedAt);
    }

    public void cancel(
            String pgOrderId,
            Long projectId,
            Money paymentAmount,
            Instant cancelledAt
    ) {
        if (status != Status.COMPLETED) {
            throw new IllegalStateException("완료된 주문 결제만 취소할 수 있습니다.");
        }
        if (!Objects.equals(this.pgOrderId, pgOrderId)
                || !Objects.equals(this.projectId, projectId)
                || !Objects.equals(this.paymentAmount, paymentAmount)) {
            throw new IllegalArgumentException("주문 결제 완료 사실과 취소 사실의 원본이 일치하지 않습니다.");
        }
        if (cancelledAt == null || cancelledAt.isBefore(completedAt)) {
            throw new IllegalArgumentException("주문 결제 취소 시각은 완료 시각보다 빠를 수 없습니다.");
        }
        this.status = Status.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    public void confirmReconciliation() {
        if (status != Status.COMPLETED) {
            throw new IllegalStateException("완료된 주문 결제만 대사 완료 처리할 수 있습니다.");
        }
        this.reconciliationStatus = ReconciliationStatus.CONFIRMED;
    }

    public void requireReview() {
        if (status != Status.COMPLETED) {
            throw new IllegalStateException("완료된 주문 결제만 대사 검토 필요 처리할 수 있습니다.");
        }
        this.reconciliationStatus = ReconciliationStatus.REVIEW_REQUIRED;
    }

    public Long orderId() {
        return orderId;
    }

    public String pgOrderId() {
        return pgOrderId;
    }

    public Long projectId() {
        return projectId;
    }

    public Money paymentAmount() {
        return paymentAmount;
    }

    public Status status() {
        return status;
    }

    public ReconciliationStatus reconciliationStatus() {
        return reconciliationStatus;
    }

    public Instant occurredAt() {
        return status == Status.COMPLETED ? completedAt : cancelledAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    @PostLoad
    private void validateState() {
        validatePositive(orderId, "주문 식별자는 양수여야 합니다.");
        validatePositive(projectId, "프로젝트 식별자는 양수여야 합니다.");
        if (pgOrderId == null || pgOrderId.isBlank()) {
            throw new IllegalArgumentException("PG 정산 식별자는 필수입니다.");
        }
        if (paymentAmount == null || paymentAmount.amount().signum() <= 0) {
            throw new IllegalArgumentException("주문 결제금액은 0원보다 커야 합니다.");
        }
        Objects.requireNonNull(status, "주문 결제 결과는 필수입니다.");
        Objects.requireNonNull(reconciliationStatus, "주문 결제 대사 상태는 필수입니다.");
        Objects.requireNonNull(completedAt, "주문 결제 완료 시각은 필수입니다.");
        if (status == Status.COMPLETED && cancelledAt != null) {
            throw new IllegalArgumentException("완료 상태의 주문 결제는 취소 시각을 가질 수 없습니다.");
        }
        if (status == Status.CANCELLED && (cancelledAt == null || cancelledAt.isBefore(completedAt))) {
            throw new IllegalArgumentException("취소 상태의 주문 결제에는 유효한 취소 시각이 필요합니다.");
        }
    }

    private static void validatePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
