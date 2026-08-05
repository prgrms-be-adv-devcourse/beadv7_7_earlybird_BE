package com.growmighty.lectures.firstday.settlement.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record SettlementFeePolicySnapshot(
        BigDecimal paymentAndSettlementAgencyFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal vatRate
) {

    private static final BigDecimal CURRENT_PAYMENT_AND_SETTLEMENT_AGENCY_FEE_RATE = new BigDecimal("0.04");
    private static final BigDecimal CURRENT_PLATFORM_FEE_RATE = new BigDecimal("0.04");
    private static final BigDecimal CURRENT_VAT_RATE = new BigDecimal("0.10");
    private static final BigDecimal ONE = BigDecimal.ONE;

    public SettlementFeePolicySnapshot {
        paymentAndSettlementAgencyFeeRate = normalizeRate(
                paymentAndSettlementAgencyFeeRate,
                "결제·정산 대행 수수료율"
        );
        platformFeeRate = normalizeRate(platformFeeRate, "플랫폼 수수료율");
        vatRate = normalizeRate(vatRate, "부가가치세율");
    }

    public static SettlementFeePolicySnapshot current() {
        return of(
                CURRENT_PAYMENT_AND_SETTLEMENT_AGENCY_FEE_RATE,
                CURRENT_PLATFORM_FEE_RATE,
                CURRENT_VAT_RATE
        );
    }

    public static SettlementFeePolicySnapshot of(
            BigDecimal paymentAndSettlementAgencyFeeRate,
            BigDecimal platformFeeRate,
            BigDecimal vatRate
    ) {
        return new SettlementFeePolicySnapshot(
                paymentAndSettlementAgencyFeeRate,
                platformFeeRate,
                vatRate
        );
    }

    private static BigDecimal normalizeRate(BigDecimal rate, String name) {
        Objects.requireNonNull(rate, name + "은 필수입니다.");
        if (rate.signum() < 0 || rate.compareTo(ONE) >= 0) {
            throw new IllegalArgumentException(name + "은 0 이상 1 미만이어야 합니다.");
        }
        return rate.stripTrailingZeros();
    }
}
