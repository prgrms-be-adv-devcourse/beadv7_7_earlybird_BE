package com.growmighty.lectures.firstday.payment.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    /** orderdb.orders.id (논리) — 일괄 환불 배치의 역추적 키. 주문당 결제 1건(멱등성). */
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    /**user의 userId와 payment의 userId의 일치함을 검증하기 위한 필드*/
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pg_order_id", nullable = false, unique = true, length = 64)
    private String pgOrderId;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Version
    private Long version;

    @Column(name = "approve_idempotency_key", nullable = false, unique = true, length = 64)
    private String approveIdempotencyKey;

    @Column(name = "confirming_At")
    private LocalDateTime confirmingAt;

    @Column(name = "confirmed_At")
    private LocalDateTime confirmedAt;

    @Column(name = "canceled_At")
    private LocalDateTime canceledAt;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;


    private Payment(Long orderId, String pgOrderId, Long userId, BigDecimal amount) {
        validatePayment(orderId, pgOrderId, userId, amount);
        this.orderId = orderId;
        this.pgOrderId = pgOrderId;
        this.userId = userId;
        this.amount = amount;
        this.approveIdempotencyKey = UUID.randomUUID().toString();
        this.status = PaymentStatus.READY;
    }

    private static void validatePayment(Long orderId, String pgOrderId, Long userId, BigDecimal amount) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 식별자는 필수입니다.");
        }
        if (pgOrderId == null) {
            throw new IllegalArgumentException("PG의 주문 식별자는 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 식별자는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0원보다 커야 합니다. 입력값: " + amount);
        }
    }

    public static Payment ready(Long orderId, String pgOrderId, Long userId, BigDecimal amount) {
        return new Payment(orderId, pgOrderId, userId, amount);
    }

    public void startConfirming() {
        if(this.status != PaymentStatus.READY) {
            throw new IllegalStateException("READY 상태에서만 승인할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.CONFIRMING;
        confirmingAt = LocalDateTime.now();
    }

    public void confirm(String paymentKey) {
        if (this.status != PaymentStatus.CONFIRMING) {
            throw new IllegalStateException("CONFIRMING 상태에서만 승인할 수 있습니다. 현재 상태: " + this.status);
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("토스 paymentKey 는 필수입니다.");
        }
        this.paymentKey = paymentKey;
        this.confirmedAt = LocalDateTime.now();
        this.confirmingAt = null;
        this.status = PaymentStatus.PAID;
    }

    public void fail() {
        if (this.status != PaymentStatus.CONFIRMING) {
            throw new IllegalStateException("CONFIRMING 상태에서만 실패 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.confirmingAt = null;
    }

    public void cancel() {
        if (this.status != PaymentStatus.PAID) {
            throw new IllegalStateException("PAID 상태에서만 취소할 수 있습니다. 현재 상태: " + this.status);
        }
        this.canceledAt = LocalDateTime.now();
        this.status = PaymentStatus.CANCELLED;
    }

    public boolean isPaid() {
        return this.status == PaymentStatus.PAID;
    }

    public boolean isConfirming() {
        return this.status == PaymentStatus.CONFIRMING;
    }
}
