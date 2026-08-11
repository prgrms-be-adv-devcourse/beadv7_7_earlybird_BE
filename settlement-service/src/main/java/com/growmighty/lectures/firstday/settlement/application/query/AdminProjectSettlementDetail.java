// TODO(settlement-plan): Keep the admin query record stable and add only review or payout fields required by the controller.
package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminProjectSettlementDetail(
        Long settlementId,
        Long projectId,
        Long creatorId,
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
        String tossSellerId,
        String bankCode,
        String maskedAccountNumber,
        List<PayoutAttempt> attempts
) {

    public AdminProjectSettlementDetail {
        attempts = List.copyOf(attempts);
    }
}
