package com.growmighty.lectures.firstday.settlement.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LegacyProjectSettlementOriginal(
        Long settlementId,
        Long projectId,
        Long creatorId,
        LocalDate scheduledDate,
        String projectTitle,
        BigDecimal paymentAndSettlementAgencyFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal vatRate
) {

    public boolean needsBackfill() {
        return projectTitle == null || projectTitle.isBlank()
                || paymentAndSettlementAgencyFeeRate == null
                || platformFeeRate == null
                || vatRate == null;
    }
}
