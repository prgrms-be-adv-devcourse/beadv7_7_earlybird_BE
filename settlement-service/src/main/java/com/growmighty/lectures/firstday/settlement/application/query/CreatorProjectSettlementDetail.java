// TODO(settlement-plan): Keep creator detail independent of PG and Kafka metadata.
package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreatorProjectSettlementDetail(
        Long settlementId,
        Long projectId,
        LocalDateTime confirmedAt,
        BigDecimal paymentAndSettlementAgencyFeeRate,
        BigDecimal platformFeeRate,
        BigDecimal vatRate,
        Money baseAmount,
        Money paymentAndSettlementAgencyFeeAmount,
        Money paymentAndSettlementAgencyFeeVatAmount,
        Money platformFeeAmount,
        Money platformFeeVatAmount,
        Money otherDeductionAmount,
        Money creatorPayoutAmount,
        PayoutStatus status,
        LocalDate scheduledDate,
        LocalDateTime completedAt,
        String bankCode,
        String maskedAccountNumber
) {
}
