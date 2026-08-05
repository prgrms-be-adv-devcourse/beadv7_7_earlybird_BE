package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.converter.MoneyAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

@Embeddable
public class SettlementBreakdownJpaEmbeddable {

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "base_amount", nullable = false, precision = 19, scale = 0)
    private Money baseAmount;

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "agency_fee_amount", nullable = false, precision = 19, scale = 0)
    private Money paymentAndSettlementAgencyFeeAmount;

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "agency_fee_vat_amount", nullable = false, precision = 19, scale = 0)
    private Money paymentAndSettlementAgencyFeeVatAmount;

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "platform_fee_amount", nullable = false, precision = 19, scale = 0)
    private Money platformFeeAmount;

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "platform_fee_vat_amount", nullable = false, precision = 19, scale = 0)
    private Money platformFeeVatAmount;

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "other_deduction_amount", nullable = false, precision = 19, scale = 0)
    private Money otherDeductionAmount;

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "creator_payout_amount", nullable = false, precision = 19, scale = 0)
    private Money creatorPayoutAmount;

    protected SettlementBreakdownJpaEmbeddable() {
    }

    private SettlementBreakdownJpaEmbeddable(SettlementBreakdown breakdown) {
        this.baseAmount = breakdown.baseAmount();
        this.paymentAndSettlementAgencyFeeAmount = breakdown.paymentAndSettlementAgencyFeeAmount();
        this.paymentAndSettlementAgencyFeeVatAmount = breakdown.paymentAndSettlementAgencyFeeVatAmount();
        this.platformFeeAmount = breakdown.platformFeeAmount();
        this.platformFeeVatAmount = breakdown.platformFeeVatAmount();
        this.otherDeductionAmount = breakdown.otherDeductionAmount();
        this.creatorPayoutAmount = breakdown.creatorPayoutAmount();
    }

    static SettlementBreakdownJpaEmbeddable fromDomain(SettlementBreakdown breakdown) {
        return new SettlementBreakdownJpaEmbeddable(breakdown);
    }

    SettlementBreakdown toDomain() {
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
}
