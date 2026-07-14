package com.growmighty.lectures.firstday.settlement.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 정산(Settlement) 엔티티 - 배치가 만들어내는 "결과 테이블".
 *
 * <p>정산 도메인의 핵심은 <b>1원의 무결성</b>이다.
 * 주문 금액(orderAmount) = 수수료(feeAmount) + 지급액(payoutAmount) 가
 * <b>단 1원의 오차도 없이</b> 항상 성립해야 한다.
 *
 * <p>그래서 수수료는 반올림으로 계산하되, 지급액은 절대 따로 반올림하지 않고
 * "주문금액 - 수수료" 로 <b>나머지</b>를 취한다. 이렇게 해야 반올림 오차가 새지 않는다.
 *
 * <p>order_id 에 유니크 제약을 두어, 같은 주문이 두 번 정산되지 않도록(멱등성) 막는다.
 */
@Entity
@Table(
        name = "settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_order_id", columnNames = "order_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 멱등성 키: 한 주문은 한 번만 정산된다. */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    /** 정산 대상 주문 금액 (= 결제 금액) */
    @Column(name = "order_amount", nullable = false)
    private BigDecimal orderAmount;

    /** 플랫폼 수수료율 (예: 0.030 = 3%) */
    @Column(name = "fee_rate", nullable = false)
    private BigDecimal feeRate;

    /** 수수료 = round(orderAmount * feeRate) */
    @Column(name = "fee_amount", nullable = false)
    private BigDecimal feeAmount;

    /** 판매자 지급액 = orderAmount - feeAmount (반올림하지 않은 나머지) */
    @Column(name = "payout_amount", nullable = false)
    private BigDecimal payoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;

    private Settlement(Long orderId, Long paymentId, BigDecimal orderAmount, BigDecimal feeRate) {
        if (orderId == null || paymentId == null) {
            throw new IllegalArgumentException("정산에는 주문/결제 식별자가 필요합니다.");
        }
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("정산 대상 금액은 0원보다 커야 합니다. 입력값: " + orderAmount);
        }
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.orderAmount = orderAmount;
        this.feeRate = feeRate;

        // 수수료만 반올림(원 단위), 지급액은 나머지로 계산 → 1원 무결성 보장
        this.feeAmount = orderAmount.multiply(feeRate).setScale(0, RoundingMode.HALF_UP);
        this.payoutAmount = orderAmount.subtract(this.feeAmount);

        this.status = SettlementStatus.COMPLETED;
        this.settledAt = LocalDateTime.now();

        verifyIntegrity();
    }

    /**
     * 주문/결제 정보로부터 정산 1건을 생성한다.
     *
     * @param orderId     정산 대상 주문 id
     * @param paymentId   해당 주문의 결제 id
     * @param orderAmount 정산 대상 금액
     * @param feeRate     수수료율 (예: 0.03)
     */
    public static Settlement of(Long orderId, Long paymentId, BigDecimal orderAmount, BigDecimal feeRate) {
        return new Settlement(orderId, paymentId, orderAmount, feeRate);
    }

    /**
     * 1원의 무결성 검증: 수수료 + 지급액 == 주문금액 이어야 한다.
     * 단 1원이라도 어긋나면 정산을 만들지 않고 즉시 실패시킨다.
     */
    public void verifyIntegrity() {
        BigDecimal sum = feeAmount.add(payoutAmount);
        if (sum.compareTo(orderAmount) != 0) {
            throw new IllegalStateException(
                    "정산 무결성 위반: 수수료(%s) + 지급액(%s) = %s, 주문금액(%s) 과 일치하지 않습니다."
                            .formatted(feeAmount, payoutAmount, sum, orderAmount));
        }
    }
}
