package com.growmighty.lectures.firstday.payment.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.payment.infrastructure.security.PaymentSensitiveDataConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "payments",
    indexes = @Index(
        name = "idx_payments_status_confirming_at",
        columnList = "status, confirming_At"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long paymentId;

    /** orderdb.orders.id (논리) — 일괄 환불 배치의 역추적 키. 주문당 결제 1건(멱등성). */
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "pg_order_id", nullable = false, unique = true, length = 64)
    private String pgOrderId;

    @Convert(converter = PaymentSensitiveDataConverter.class)
    @Column(name = "payment_key", length = 512)
    private String paymentKey;

    @Version
    private Long version;

    @Convert(converter = PaymentSensitiveDataConverter.class)
    @Column(name = "approve_idempotency_key", nullable = false, length = 512)
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


    private Payment(Long orderId, BigDecimal amount) {
        validatePayment(orderId, amount);
        this.orderId = orderId;
        this.pgOrderId = generatePgOrderId(orderId);
        this.amount = amount;
        this.approveIdempotencyKey = UUID.randomUUID().toString();
        this.status = PaymentStatus.READY;
    }

    private static void validatePayment(Long orderId, BigDecimal amount) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 식별자는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0원보다 커야 합니다. 입력값: " + amount);
        }
    }

    public static Payment ready(Long orderId, BigDecimal amount) {
        return new Payment(orderId, amount);
    }

    private static String generatePgOrderId(Long orderId) {
        return "order-" + orderId + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    public void startConfirming(String paymentKey) {
        if(this.status != PaymentStatus.READY) {
            throw new IllegalStateException("READY 상태에서만 승인할 수 있습니다. 현재 상태: " + this.status);
        }

        validatePaymentKey(paymentKey);

        this.paymentKey = paymentKey;
        this.status = PaymentStatus.CONFIRMING;
        confirmingAt = LocalDateTime.now();
    }

    public void confirm(String paymentKey) {
        if (this.status != PaymentStatus.CONFIRMING) {
            throw new IllegalStateException("CONFIRMING 상태에서만 승인할 수 있습니다. 현재 상태: " + this.status);
        }
        if (!this.paymentKey.equals(paymentKey)) {
            throw new IllegalStateException("승인 요청 paymentKey가 일치하지 않습니다.");
        }
        this.confirmedAt = LocalDateTime.now();
        this.confirmingAt = null;
        this.status = PaymentStatus.PAID;
    }

    private static void validatePaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("토스 paymentKey는 필수입니다.");
        }
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

    public boolean isReady() {
        return this.status == PaymentStatus.READY;
    }

    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    public boolean isCancelled() {
        return this.status == PaymentStatus.CANCELLED;
    }

    public boolean isPaid() {
        return this.status == PaymentStatus.PAID;
    }

    public boolean isConfirming() {
        return this.status == PaymentStatus.CONFIRMING;
    }

    private boolean isConfirmingExpired(LocalDateTime now, Duration maximumConfirmingDuration) {
        if (!isConfirming()) {
            return false;
        }

        LocalDateTime expiredAt = confirmingAt.plus(maximumConfirmingDuration);
        return !now.isBefore(expiredAt);
    }

    public boolean failIfConfirmingExpired(LocalDateTime now, Duration maximumConfirmingDuration) {
        if (!isConfirmingExpired(now, maximumConfirmingDuration)) {
            return false;
        }
        this.status = PaymentStatus.FAILED;
        this.confirmingAt = null;
        return true;
    }

    public boolean reconcileConfirmed(String paymentKey) {
        if (isPaid()) {
            return false;
        }

        if (!isConfirming() && !isFailed()) {
            throw new IllegalStateException("CONFIRMING 또는 FAILED 상태의 결제만 승인 정합화할 수 있습니다. status = " + this.status);
        }

        if(!this.paymentKey.equals(paymentKey)) {
            throw new IllegalStateException("승인 요청 paymentKey가 일치하지 않습니다.");
        }

        this.confirmedAt = LocalDateTime.now();
        this.confirmingAt = null;
        this.status = PaymentStatus.PAID;
        return true;
    }

    public boolean reconcileFailed() {
        if (isFailed()) {
            return false;
        }

        if (!isConfirming()) {
            return false;
        }

        this.status = PaymentStatus.FAILED;
        this.confirmingAt = null;
        return true;
    }

    public void validateApproval(
        String approvedPaymentKey,
        String approvedPgOrderId,
        BigDecimal approvedAmount
    ) {
        if (!this.paymentKey.equals(approvedPaymentKey)) {
            throw new IllegalStateException("PG paymentKey가 일치하지 않습니다.");
        }

        if (!this.pgOrderId.equals(approvedPgOrderId)) {
            throw new IllegalStateException("PG 주문번호가 일치하지 않습니다.");
        }

        if (this.amount.compareTo(approvedAmount) != 0) {
            throw new IllegalStateException("PG 승인 금액이 일치하지 않습니다.");
        }
    }
}
