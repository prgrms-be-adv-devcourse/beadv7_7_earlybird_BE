package com.growmighty.lectures.firstday.order.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_payment_status_outboxes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_payment_status_outboxes_order_status",
                columnNames = {"order_id", "order_status"}),
        indexes = @Index(
                name = "idx_order_payment_status_outboxes_pending",
                columnList = "status, next_retry_at, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaymentStatusOutbox extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "pg_order_id", nullable = false, updatable = false)
    private String pgOrderId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "payment_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, updatable = false)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderPaymentStatusOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "published_at")
    private Instant publishedAt;

    private OrderPaymentStatusOutbox(Long orderId, String pgOrderId, Long projectId,
                                     BigDecimal paymentAmount, OrderStatus orderStatus, Instant occurredAt) {
        if (orderId == null || orderId <= 0 || projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("Order payment status event identifiers are required.");
        }
        if (pgOrderId == null || pgOrderId.isBlank()) {
            throw new IllegalArgumentException("Order payment status event pgOrderId is required.");
        }
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order payment status event amount must be positive.");
        }
        if (orderStatus != OrderStatus.PAID && orderStatus != OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Only final successful payment statuses can be published.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Order payment status event occurredAt is required.");
        }
        this.eventId = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.orderId = orderId;
        this.pgOrderId = pgOrderId;
        this.projectId = projectId;
        this.paymentAmount = paymentAmount;
        this.orderStatus = orderStatus;
        this.status = OrderPaymentStatusOutboxStatus.PENDING;
        this.nextRetryAt = occurredAt;
    }

    public static OrderPaymentStatusOutbox pending(Long orderId, String pgOrderId, Long projectId,
                                                   BigDecimal paymentAmount, OrderStatus orderStatus,
                                                   Instant occurredAt) {
        return new OrderPaymentStatusOutbox(orderId, pgOrderId, projectId, paymentAmount, orderStatus, occurredAt);
    }

    public void published(Instant now) {
        if (status == OrderPaymentStatusOutboxStatus.PUBLISHED) {
            return;
        }
        status = OrderPaymentStatusOutboxStatus.PUBLISHED;
        lastAttemptAt = now;
        publishedAt = now;
        lastError = null;
    }

    public void recordFailure(Instant attemptedAt, Instant retryAt, String error) {
        if (status == OrderPaymentStatusOutboxStatus.PUBLISHED) {
            return;
        }
        retryCount++;
        lastAttemptAt = attemptedAt;
        nextRetryAt = retryAt;
        lastError = error;
    }
}
