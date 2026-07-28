package com.growmighty.lectures.firstday.settlement.application.port;

import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.math.BigDecimal;
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

    private static final int MAX_REF_PAYOUT_ID_LENGTH = 50;
    private static final int MAX_SELLER_ID_LENGTH = 35;
    private static final int MAX_TRANSACTION_DESCRIPTION_LENGTH = 7;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 300;
    private static final BigDecimal MAX_PAYOUT_AMOUNT_EXCLUSIVE = BigDecimal.valueOf(1_000_000_000L);

    public ScheduledPayoutRequest {
        refPayoutId = requireText(refPayoutId, "지급대행 참조 식별자");
        sellerId = requireText(sellerId, "토스 셀러 식별자");
        payoutDate = Objects.requireNonNull(payoutDate, "지급 예정일은 필수입니다.");
        amount = Objects.requireNonNull(amount, "지급 금액은 필수입니다.");
        transactionDescription = requireText(transactionDescription, "이체 적요");
        idempotencyKey = requireText(idempotencyKey, "멱등키");

        requireMaxLength(refPayoutId, MAX_REF_PAYOUT_ID_LENGTH, "지급대행 참조 식별자");
        requireMaxLength(sellerId, MAX_SELLER_ID_LENGTH, "토스 셀러 식별자");
        requireMaxLength(
                transactionDescription,
                MAX_TRANSACTION_DESCRIPTION_LENGTH,
                "이체 적요"
        );
        requireMaxLength(idempotencyKey, MAX_IDEMPOTENCY_KEY_LENGTH, "멱등키");

        if (amount.amount().signum() <= 0
                || amount.amount().compareTo(MAX_PAYOUT_AMOUNT_EXCLUSIVE) >= 0) {
            throw new IllegalArgumentException("지급 금액은 0원보다 크고 10억원보다 작아야 합니다.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
        return value;
    }

    private static void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "는 " + maxLength + "자 이하여야 합니다.");
        }
    }
}
