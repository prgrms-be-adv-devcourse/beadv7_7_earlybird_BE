package com.growmighty.lectures.firstday.payment.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** orderdb.orders.id (논리) — 일괄 환불 배치의 역추적 키. 주문당 결제 1건(멱등성). */
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column
    private String pgTransactionId;

    private Payment(Long orderId, BigDecimal amount) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 식별자는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0원보다 커야 합니다. 입력값: " + amount);
        }
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public static Payment ready(Long orderId, BigDecimal amount) {
        return new Payment(orderId, amount);
    }

    public void approve(String pgTransactionId) {
        if (this.status != PaymentStatus.READY) {
            throw new IllegalStateException("승인 대기(READY) 상태에서만 승인할 수 있습니다. 현재 상태: " + this.status);
        }
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.PAID;
    }

    public void fail() {
        if (this.status != PaymentStatus.READY) {
            throw new IllegalStateException("승인 대기(READY) 상태에서만 실패 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        if (this.status != PaymentStatus.PAID) {
            throw new IllegalStateException("결제 완료(PAID) 상태에서만 취소할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public boolean isPaid() {
        return this.status == PaymentStatus.PAID;
    }
}
