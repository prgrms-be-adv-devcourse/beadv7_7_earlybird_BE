package com.growmighty.lectures.firstday.settlement.application.port;

import java.math.BigDecimal;

public record LegacyProjectSettlementOriginal(
        Long settlementId,
        Long projectId,
        Long creatorId,
        BigDecimal paymentAndSettlementAgencyFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal vatRate
) {

    public boolean needsBackfill() {
        return paymentAndSettlementAgencyFeeRate == null
                || platformFeeRate == null
                || vatRate == null;
    }
}
