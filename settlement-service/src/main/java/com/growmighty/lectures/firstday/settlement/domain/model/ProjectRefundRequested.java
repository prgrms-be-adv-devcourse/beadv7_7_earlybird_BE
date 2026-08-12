package com.growmighty.lectures.firstday.settlement.domain.model;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
        name = "project_refund_requested_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_refund_requested_project_id",
                columnNames = "project_id"
        )
)
public class ProjectRefundRequested extends BaseEntity {

    public static final String EVENT_TYPE = "ProjectRefundRequested";
    public static final int SCHEMA_VERSION = 1;

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String refundRequestId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 30)
    private ProjectCancellationReason reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "project_refund_requested_payments",
            joinColumns = @JoinColumn(name = "event_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_project_refund_requested_payment_order",
                    columnNames = {"event_id", "order_id"}
            )
    )
    @OrderBy("orderId ASC")
    private List<Payment> payments;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    private Long version;

    protected ProjectRefundRequested() {
    }

    private ProjectRefundRequested(
            String refundRequestId,
            Long projectId,
            ProjectCancellationReason reason,
            List<Payment> payments,
            Instant occurredAt
    ) {
        this.refundRequestId = refundRequestId;
        this.projectId = projectId;
        this.reason = reason;
        this.payments = List.copyOf(payments);
        this.occurredAt = occurredAt;
        validateState();
    }

    public static ProjectRefundRequested request(
            String refundRequestId,
            ProjectOutcomeFact outcome,
            List<OrderPaymentFact> payments,
            Instant occurredAt
    ) {
        Objects.requireNonNull(outcome, "프로젝트 결과 사실은 필수입니다.");
        if (!outcome.requiresRefund()) {
            throw new IllegalArgumentException("실패하거나 취소된 프로젝트만 환불을 요청할 수 있습니다.");
        }
        List<Payment> requestedPayments = List.copyOf(payments).stream()
                .map(payment -> {
                    if (!outcome.projectId().equals(payment.projectId())
                            || payment.status() != OrderPaymentFact.Status.COMPLETED
                            || payment.completedAt().isAfter(outcome.occurredAt())) {
                        throw new IllegalArgumentException("프로젝트의 유효한 결제 전체만 환불 요청에 포함할 수 있습니다.");
                    }
                    return new Payment(payment.orderId(), payment.pgOrderId());
                })
                .sorted((left, right) -> left.orderId.compareTo(right.orderId))
                .toList();
        return new ProjectRefundRequested(
                refundRequestId,
                outcome.projectId(),
                cancellationReason(outcome.outcome()),
                requestedPayments,
                occurredAt
        );
    }

    public void markPublished(Instant publishedAt) {
        if (this.publishedAt != null) {
            return;
        }
        Instant completedAt = Objects.requireNonNull(publishedAt, "Outbox 발행 완료 시각은 필수입니다.");
        if (completedAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("Outbox 발행 완료 시각은 이벤트 발생 시각보다 빠를 수 없습니다.");
        }
        this.publishedAt = completedAt;
    }

    public String refundRequestId() {
        return refundRequestId;
    }

    public Long projectId() {
        return projectId;
    }

    public ProjectCancellationReason reason() {
        return reason;
    }

    public List<Payment> payments() {
        return List.copyOf(payments);
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public boolean published() {
        return publishedAt != null;
    }

    private static ProjectCancellationReason cancellationReason(ProjectOutcomeFact.Outcome outcome) {
        return switch (outcome) {
            case FAILED -> ProjectCancellationReason.PROJECT_FAILED;
            case CANCELLED -> ProjectCancellationReason.PROJECT_CANCELLED;
            case SUCCEEDED -> throw new IllegalArgumentException("성공 프로젝트는 환불 요청 사유가 없습니다.");
        };
    }

    @PostLoad
    private void validateState() {
        if (refundRequestId == null || refundRequestId.isBlank()) {
            throw new IllegalArgumentException("환불 요청 식별자는 필수입니다.");
        }
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        Objects.requireNonNull(reason, "프로젝트 환불 요청 사유는 필수입니다.");
        Objects.requireNonNull(occurredAt, "이벤트 발생 시각은 필수입니다.");
        if (payments == null || payments.isEmpty()) {
            throw new IllegalArgumentException("프로젝트 환불 요청에는 하나 이상의 결제가 필요합니다.");
        }
        Set<Long> orderIds = new HashSet<>();
        Set<String> pgOrderIds = new HashSet<>();
        if (payments.stream().anyMatch(payment -> !orderIds.add(payment.orderId)
                || !pgOrderIds.add(payment.pgOrderId))) {
            throw new IllegalArgumentException("프로젝트 환불 요청의 결제 식별자는 중복될 수 없습니다.");
        }
    }

    @Embeddable
    public static class Payment {

        @Column(name = "order_id", nullable = false, updatable = false)
        private Long orderId;

        @Column(name = "pg_order_id", nullable = false, updatable = false, length = 100)
        private String pgOrderId;

        protected Payment() {
        }

        private Payment(Long orderId, String pgOrderId) {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
            }
            if (pgOrderId == null || pgOrderId.isBlank()) {
                throw new IllegalArgumentException("PG 정산 식별자는 필수입니다.");
            }
            this.orderId = orderId;
            this.pgOrderId = pgOrderId;
        }

        public Long orderId() {
            return orderId;
        }

        public String pgOrderId() {
            return pgOrderId;
        }
    }
}
