// TODO(settlement-plan): Persist the confirmed rate snapshot exactly and remove no-longer-used policy fields.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
public class SettlementFeePolicySnapshotJpaEmbeddable {

    @Column(name = "payment_and_settlement_agency_fee_rate", precision = 7, scale = 6, updatable = false)
    private BigDecimal paymentAndSettlementAgencyFeeRate;

    @Column(name = "platform_fee_rate", precision = 7, scale = 6, updatable = false)
    private BigDecimal platformFeeRate;

    @Column(name = "fee_vat_rate", precision = 7, scale = 6, updatable = false)
    private BigDecimal vatRate;

    protected SettlementFeePolicySnapshotJpaEmbeddable() {
    }

    private SettlementFeePolicySnapshotJpaEmbeddable(ProjectSettlement settlement) {
        this.paymentAndSettlementAgencyFeeRate = settlement.paymentAndSettlementAgencyFeeRate();
        this.platformFeeRate = settlement.platformFeeRate();
        this.vatRate = settlement.vatRate();
    }

    static SettlementFeePolicySnapshotJpaEmbeddable fromDomain(ProjectSettlement settlement) {
        return new SettlementFeePolicySnapshotJpaEmbeddable(settlement);
    }

    BigDecimal paymentAndSettlementAgencyFeeRate() {
        return paymentAndSettlementAgencyFeeRate;
    }

    BigDecimal platformFeeRate() {
        return platformFeeRate;
    }

    BigDecimal vatRate() {
        return vatRate;
    }
}
