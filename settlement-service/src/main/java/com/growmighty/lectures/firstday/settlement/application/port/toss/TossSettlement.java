package com.growmighty.lectures.firstday.settlement.application.port.toss;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

public record TossSettlement(
        String orderId,
        String currency,
        Money amount,
        OffsetDateTime approvedAt,
        LocalDate soldDate
) {

    public TossSettlement {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("토스 주문 식별자는 필수입니다.");
        }
        if (!"KRW".equals(currency)) {
            throw new IllegalArgumentException("토스 정산 통화는 KRW여야 합니다.");
        }
        Objects.requireNonNull(amount, "토스 정산 금액은 필수입니다.");
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("토스 정산 금액은 0원보다 커야 합니다.");
        }
        Objects.requireNonNull(approvedAt, "토스 승인 시각은 필수입니다.");
        Objects.requireNonNull(soldDate, "토스 매출일은 필수입니다.");
    }
}
