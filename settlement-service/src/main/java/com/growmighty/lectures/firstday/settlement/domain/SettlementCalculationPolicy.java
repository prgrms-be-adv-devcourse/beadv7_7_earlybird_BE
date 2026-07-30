package com.growmighty.lectures.firstday.settlement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class SettlementCalculationPolicy {

    private final SettlementFeePolicySnapshot feePolicySnapshot;

    private SettlementCalculationPolicy(SettlementFeePolicySnapshot feePolicySnapshot) {
        this.feePolicySnapshot = feePolicySnapshot;
    }

    public static SettlementCalculationPolicy current() {
        return new SettlementCalculationPolicy(SettlementFeePolicySnapshot.current());
    }

    public SettlementFeePolicySnapshot feePolicySnapshot() {
        return feePolicySnapshot;
    }

    public SettlementBreakdown calculate(List<Money> orderPaymentAmounts) {
        Objects.requireNonNull(orderPaymentAmounts, "주문 결제금액 목록은 필수입니다.");

        Money baseAmount = Money.wons(orderPaymentAmounts.stream()
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (baseAmount.amount().signum() == 0) {
            throw new IllegalArgumentException("프로젝트 정산 기준 금액은 0원보다 커야 합니다.");
        }
        Money paymentAndSettlementAgencyFeeAmount = Money.wons(orderPaymentAmounts.stream()
                .map(Money::amount)
                .map(amount -> applyRate(amount, feePolicySnapshot.paymentAndSettlementAgencyFeeRate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        Money paymentAndSettlementAgencyFeeVatAmount = Money.wons(applyRate(
                paymentAndSettlementAgencyFeeAmount.amount(),
                feePolicySnapshot.vatRate()
        ));
        Money platformFeeAmount = Money.wons(applyRate(
                baseAmount.amount(),
                feePolicySnapshot.platformFeeRate()
        ));
        Money platformFeeVatAmount = Money.wons(applyRate(
                platformFeeAmount.amount(),
                feePolicySnapshot.vatRate()
        ));
        Money otherDeductionAmount = Money.wons(0);
        Money creatorPayoutAmount = baseAmount
                .minus(paymentAndSettlementAgencyFeeAmount)
                .minus(paymentAndSettlementAgencyFeeVatAmount)
                .minus(platformFeeAmount)
                .minus(platformFeeVatAmount)
                .minus(otherDeductionAmount);

        return SettlementBreakdown.of(
                baseAmount,
                paymentAndSettlementAgencyFeeAmount,
                paymentAndSettlementAgencyFeeVatAmount,
                platformFeeAmount,
                platformFeeVatAmount,
                otherDeductionAmount,
                creatorPayoutAmount
        );
    }

    private static BigDecimal applyRate(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(0, RoundingMode.DOWN);
    }
}
