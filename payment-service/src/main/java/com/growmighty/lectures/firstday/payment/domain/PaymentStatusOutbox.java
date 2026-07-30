package com.growmighty.lectures.firstday.payment.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "payment_status_outbox",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_status_outbox_payment_id_payment_status",
        columnNames = {"payment_id", "payment_status"}
    ),
    indexes = @Index(
        name = "idx_payment_status_outbox_status_id",
        columnList = "status, id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentStatusOutbox extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatusOutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime sentAt;

    private PaymentStatusOutbox(
        Long paymentId,
        Long orderId,
        PaymentStatus paymentStatus
    ) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.paymentStatus = paymentStatus;
        this.status = PaymentStatusOutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public static PaymentStatusOutbox pending(
        Long paymentId,
        Long orderId,
        PaymentStatus paymentStatus
    ) {
        return new PaymentStatusOutbox(paymentId, orderId, paymentStatus);
    }

    public void markSent() {
        if (this.status == PaymentStatusOutboxStatus.SENT) {
            return;
        }

        this.status = PaymentStatusOutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void increaseRetryCount() {
        this.retryCount++;
    }

    public boolean isPending() {
        return this.status == PaymentStatusOutboxStatus.PENDING;
    }
}
