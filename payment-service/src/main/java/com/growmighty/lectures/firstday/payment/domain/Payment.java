package com.growmighty.lectures.firstday.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column
    private String pgTransactionId;

    private Payment(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0원보다 커야 합니다. 입력값: " + amount);
        }
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public static Payment ready(BigDecimal amount) {
        return new Payment(amount);
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
