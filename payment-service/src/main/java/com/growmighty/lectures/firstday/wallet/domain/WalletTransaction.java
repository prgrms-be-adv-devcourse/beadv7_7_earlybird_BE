package com.growmighty.lectures.firstday.wallet.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 예치금 충전/사용/환불 이력. amount 는 항상 양수 — 방향은 {@link WalletTransactionType} 이 결정한다. */
@Entity
@Table(name = "wallet_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletTransactionType type;

    @Column(nullable = false)
    private BigDecimal amount;

    /** 처리 후 잔액 — 검증용 스냅샷 */
    @Column(nullable = false)
    private BigDecimal balanceAfter;

    /** USE, REFUND 시 payments.id (논리) */
    @Column
    private Long paymentId;

    /** CHARGE 시 PG 거래번호 */
    @Column
    private String pgTransactionId;

    private WalletTransaction(Long walletId, WalletTransactionType type, BigDecimal amount, BigDecimal balanceAfter,
                              Long paymentId, String pgTransactionId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("금액은 0원보다 커야 합니다. 입력값: " + amount);
        }
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.paymentId = paymentId;
        this.pgTransactionId = pgTransactionId;
    }

    public static WalletTransaction charge(Long walletId, BigDecimal amount, BigDecimal balanceAfter, String pgTransactionId) {
        return new WalletTransaction(walletId, WalletTransactionType.CHARGE, amount, balanceAfter, null, pgTransactionId);
    }

    public static WalletTransaction use(Long walletId, BigDecimal amount, BigDecimal balanceAfter, Long paymentId) {
        return new WalletTransaction(walletId, WalletTransactionType.USE, amount, balanceAfter, paymentId, null);
    }

    public static WalletTransaction refund(Long walletId, BigDecimal amount, BigDecimal balanceAfter, Long paymentId) {
        return new WalletTransaction(walletId, WalletTransactionType.REFUND, amount, balanceAfter, paymentId, null);
    }
}
