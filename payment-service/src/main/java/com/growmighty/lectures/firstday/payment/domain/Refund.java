package com.growmighty.lectures.firstday.payment.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 일괄 환불 추적 — reason/status 로 배치 실패·재시도를 추적한다. */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

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
        this.status = RefundStatus.REQUESTED;
    }

    public static Refund request(Long paymentId, BigDecimal amount, RefundReason reason) {
        return new Refund(paymentId, amount, reason);
    }

    public void complete() {
        if (this.status == RefundStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 환불입니다.");
        }
        this.status = RefundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = RefundStatus.FAILED;
    }
}
