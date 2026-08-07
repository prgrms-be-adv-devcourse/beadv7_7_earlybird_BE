package com.growmighty.lectures.firstday.refund.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.payment.infrastructure.security.PaymentSensitiveDataConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** 일괄 환불 추적 — reason/status 로 배치 실패·재시도를 추적한다. */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long paymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Convert(converter = PaymentSensitiveDataConverter.class)
    @Column(name = "cancel_idempotency_key", nullable = false, length = 512)
    private String cancelIdempotencyKey;

    /** 실패 시 null — 재시도 대상 */
    @Column
    private LocalDateTime completedAt;

    private Refund(Long paymentId, BigDecimal amount, RefundReason reason) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("환불 금액은 0원보다 커야 합니다. 입력값: " + amount);
        }
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
        this.cancelIdempotencyKey = UUID.randomUUID().toString();
        this.status = RefundStatus.REQUESTED;
    }

    public static Refund request(Long paymentId, BigDecimal amount, RefundReason reason) {
        return new Refund(paymentId, amount, reason);
    }

    public boolean isRequested() {
        return RefundStatus.REQUESTED == this.status;
    }

    public void complete() {
        if (!isRequested()) {
            throw new IllegalStateException("REQUESTED 상태의 환불만 완료할 수 있습니다. status = " + this.status);
        }
        this.status = RefundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        if (!isRequested()) {
            throw new IllegalStateException("REQUESTED 상태의 환불만 실패 처리할 수 있습니다. status = " + this.status);
        }

        this.status = RefundStatus.FAILED;
    }
}
