package com.growmighty.lectures.firstday.settlement.domain.model;

import java.util.Objects;

public final class SettlementBreakdown {

    private final Money baseAmount;
    private final Money paymentAndSettlementAgencyFeeAmount;
    private final Money paymentAndSettlementAgencyFeeVatAmount;
    private final Money platformFeeAmount;
    private final Money platformFeeVatAmount;
    private final Money otherDeductionAmount;
    private final Money creatorPayoutAmount;

    private SettlementBreakdown(
            Money baseAmount,
            Money paymentAndSettlementAgencyFeeAmount,
            Money paymentAndSettlementAgencyFeeVatAmount,
            Money platformFeeAmount,
            Money platformFeeVatAmount,
            Money otherDeductionAmount,
            Money creatorPayoutAmount
    ) {
        this.baseAmount = Objects.requireNonNull(baseAmount, "프로젝트 정산 기준 금액은 필수입니다.");
        this.paymentAndSettlementAgencyFeeAmount = Objects.requireNonNull(
                paymentAndSettlementAgencyFeeAmount,
                "결제·정산 대행 수수료는 필수입니다."
        );
        this.paymentAndSettlementAgencyFeeVatAmount = Objects.requireNonNull(
                paymentAndSettlementAgencyFeeVatAmount,
                "결제·정산 대행 수수료 부가세는 필수입니다."
        );
        this.platformFeeAmount = Objects.requireNonNull(platformFeeAmount, "플랫폼 수수료는 필수입니다.");
        this.platformFeeVatAmount = Objects.requireNonNull(platformFeeVatAmount, "플랫폼 수수료 부가세는 필수입니다.");
        this.otherDeductionAmount = Objects.requireNonNull(otherDeductionAmount, "기타 공제액은 필수입니다.");
        this.creatorPayoutAmount = Objects.requireNonNull(creatorPayoutAmount, "창작자 지급액은 필수입니다.");
        verifyIntegrity();
    }

    public static SettlementBreakdown of(
            Money baseAmount,
            Money paymentAndSettlementAgencyFeeAmount,
            Money paymentAndSettlementAgencyFeeVatAmount,
            Money platformFeeAmount,
            Money platformFeeVatAmount,
            Money otherDeductionAmount,
            Money creatorPayoutAmount
    ) {
        return new SettlementBreakdown(
                baseAmount,
                paymentAndSettlementAgencyFeeAmount,
                paymentAndSettlementAgencyFeeVatAmount,
                platformFeeAmount,
                platformFeeVatAmount,
                otherDeductionAmount,
                creatorPayoutAmount
        );
    }

    public Money baseAmount() {
        return baseAmount;
    }

    public Money paymentAndSettlementAgencyFeeAmount() {
        return paymentAndSettlementAgencyFeeAmount;
    }

    public Money paymentAndSettlementAgencyFeeVatAmount() {
        return paymentAndSettlementAgencyFeeVatAmount;
    }

    public Money platformFeeAmount() {
        return platformFeeAmount;
    }

    public Money platformFeeVatAmount() {
        return platformFeeVatAmount;
    }

    public Money otherDeductionAmount() {
        return otherDeductionAmount;
    }

    public Money creatorPayoutAmount() {
        return creatorPayoutAmount;
    }

    private void verifyIntegrity() {
        Money calculatedPayoutAmount = baseAmount
                .minus(paymentAndSettlementAgencyFeeAmount)
                .minus(paymentAndSettlementAgencyFeeVatAmount)
                .minus(platformFeeAmount)
                .minus(platformFeeVatAmount)
                .minus(otherDeductionAmount);

        if (!calculatedPayoutAmount.equals(creatorPayoutAmount)) {
            throw new IllegalArgumentException("정산 금액 명세의 창작자 지급액이 공제 후 금액과 일치하지 않습니다.");
        }
    }
}
