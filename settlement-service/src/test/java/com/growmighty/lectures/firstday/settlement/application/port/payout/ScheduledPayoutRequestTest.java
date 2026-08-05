package com.growmighty.lectures.firstday.settlement.application.port.payout;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledPayoutRequestTest {

    private static final LocalDate PAYOUT_DATE = LocalDate.of(2026, 8, 3);

    @Test
    @DisplayName("0원 이하의 지급 금액을 거부한다")
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> request(Money.wons(0), "얼리버드"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0원보다 커야");
    }

    @Test
    @DisplayName("지급 흐름에 필요한 식별자가 비어 있으면 거부한다")
    void rejectsBlankIdentifiers() {
        assertThatThrownBy(() -> new ScheduledPayoutRequest(
                " ",
                "seller-1",
                PAYOUT_DATE,
                Money.wons(10_000),
                "얼리버드",
                "idempotency-1"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("참조 식별자");

        assertThatThrownBy(() -> new ScheduledPayoutRequest(
                "payout-1",
                "seller-1",
                PAYOUT_DATE,
                Money.wons(10_000),
                "얼리버드",
                " "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("멱등키");
    }

    private static ScheduledPayoutRequest request(Money amount, String description) {
        return new ScheduledPayoutRequest(
                "payout-1",
                "seller-1",
                PAYOUT_DATE,
                amount,
                description,
                "idempotency-1"
        );
    }
}
