package com.growmighty.lectures.firstday.wallet.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 예치금 지갑 — 1인 1지갑(1:1, 논리 참조). 잔액은 항상 0 이상이어야 한다. */
@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal balance;

    private Wallet(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
    }

    public static Wallet open(Long userId) {
        return new Wallet(userId);
    }

    public void charge(BigDecimal amount) {
        validatePositive(amount);
        this.balance = this.balance.add(amount);
    }

    public void use(BigDecimal amount) {
        validatePositive(amount);
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                "예치금 잔액이 부족합니다. 잔액=" + this.balance + ", 요청=" + amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void refund(BigDecimal amount) {
        validatePositive(amount);
        this.balance = this.balance.add(amount);
    }

    private void validatePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("금액은 0원보다 커야 합니다. 입력값: " + amount);
        }
    }
}
