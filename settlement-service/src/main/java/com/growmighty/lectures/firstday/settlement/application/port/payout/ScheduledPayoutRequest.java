// TODO(settlement-plan): Carry refPayoutId, destination, schedule, KRW amount, description, and metadata in Toss-compatible form.
package com.growmighty.lectures.firstday.settlement.application.port.payout;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.time.LocalDate;
import java.util.Objects;

public record ScheduledPayoutRequest(
        String refPayoutId,
        String sellerId,
        LocalDate payoutDate,
        Money amount,
        String transactionDescription,
        String idempotencyKey
) {

    public ScheduledPayoutRequest {
        refPayoutId = requireText(refPayoutId, "지급대행 참조 식별자");
        sellerId = requireText(sellerId, "지급 대상 식별자");
        payoutDate = Objects.requireNonNull(payoutDate, "지급 예정일은 필수입니다.");
        amount = Objects.requireNonNull(amount, "지급 금액은 필수입니다.");
        transactionDescription = requireText(transactionDescription, "이체 적요");
        idempotencyKey = requireText(idempotencyKey, "멱등키");

        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("지급 금액은 0원보다 커야 합니다.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
        return value;
    }

}
