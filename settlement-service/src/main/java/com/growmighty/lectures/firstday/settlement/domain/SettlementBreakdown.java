package com.growmighty.lectures.firstday.settlement.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.util.Objects;

@Embeddable
public class SettlementBreakdown {

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "base_amount", nullable = false, precision = 19, scale = 0))
    private Money baseAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "agency_fee_amount", nullable = false, precision = 19, scale = 0))
    private Money paymentAndSettlementAgencyFeeAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "agency_fee_vat_amount", nullable = false, precision = 19, scale = 0))
    private Money paymentAndSettlementAgencyFeeVatAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "platform_fee_amount", nullable = false, precision = 19, scale = 0))
    private Money platformFeeAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "platform_fee_vat_amount", nullable = false, precision = 19, scale = 0))
    private Money platformFeeVatAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "other_deduction_amount", nullable = false, precision = 19, scale = 0))
    private Money otherDeductionAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "creator_payout_amount", nullable = false, precision = 19, scale = 0))
    private Money creatorPayoutAmount;

    protected SettlementBreakdown() {
    }

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
