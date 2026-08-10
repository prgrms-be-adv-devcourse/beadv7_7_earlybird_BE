// TODO(settlement-plan): Keep persistence mapping aligned with the domain breakdown without recalculating values.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
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

    private SettlementBreakdownJpaEmbeddable(ProjectSettlement settlement) {
        this.baseAmount = settlement.baseAmount();
        this.paymentAndSettlementAgencyFeeAmount = settlement.paymentAndSettlementAgencyFeeAmount();
        this.paymentAndSettlementAgencyFeeVatAmount = settlement.paymentAndSettlementAgencyFeeVatAmount();
        this.platformFeeAmount = settlement.platformFeeAmount();
        this.platformFeeVatAmount = settlement.platformFeeVatAmount();
        this.otherDeductionAmount = settlement.otherDeductionAmount();
        this.creatorPayoutAmount = settlement.creatorPayoutAmount();
    }

    static SettlementBreakdownJpaEmbeddable fromDomain(ProjectSettlement settlement) {
        return new SettlementBreakdownJpaEmbeddable(settlement);
    }

    Money baseAmount() {
        return baseAmount;
    }

    Money paymentAndSettlementAgencyFeeAmount() {
        return paymentAndSettlementAgencyFeeAmount;
    }

    Money paymentAndSettlementAgencyFeeVatAmount() {
        return paymentAndSettlementAgencyFeeVatAmount;
    }

    Money platformFeeAmount() {
        return platformFeeAmount;
    }

    Money platformFeeVatAmount() {
        return platformFeeVatAmount;
    }

    Money otherDeductionAmount() {
        return otherDeductionAmount;
    }

    Money creatorPayoutAmount() {
        return creatorPayoutAmount;
    }
}
