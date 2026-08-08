// TODO(settlement-plan): Persist the confirmed rate snapshot exactly and remove no-longer-used policy fields.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.settlement.domain.model.SettlementFeePolicySnapshot;
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

    private SettlementFeePolicySnapshotJpaEmbeddable(SettlementFeePolicySnapshot snapshot) {
        this.paymentAndSettlementAgencyFeeRate = snapshot.paymentAndSettlementAgencyFeeRate();
        this.platformFeeRate = snapshot.platformFeeRate();
        this.vatRate = snapshot.vatRate();
    }

    static SettlementFeePolicySnapshotJpaEmbeddable fromDomain(SettlementFeePolicySnapshot snapshot) {
        return new SettlementFeePolicySnapshotJpaEmbeddable(snapshot);
    }

    SettlementFeePolicySnapshot toDomain() {
        return SettlementFeePolicySnapshot.of(
                paymentAndSettlementAgencyFeeRate,
                platformFeeRate,
                vatRate
        );
    }
}
