package com.growmighty.lectures.firstday.settlement.application.port;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScheduledPayoutRequestTest {

    private static final LocalDate PAYOUT_DATE = LocalDate.of(2026, 8, 3);

    @Test
    @DisplayName("토스가 허용하는 범위를 벗어난 지급 금액을 거부한다")
    void rejectsOutOfRangeAmount() {
        assertThatThrownBy(() -> request(Money.wons(0), "얼리버드"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10억원보다 작아야");

        assertThatThrownBy(() -> request(Money.wons(1_000_000_000L), "얼리버드"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10억원보다 작아야");
    }

    @Test
    @DisplayName("이체 적요가 7자를 넘으면 요청을 거부한다")
    void rejectsLongTransactionDescription() {
        assertThatThrownBy(() -> request(Money.wons(10_000), "여덟글자적요이다"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7자 이하");
    }

    @Test
    @DisplayName("토스 지급 및 멱등 식별자 길이 제한을 지킨다")
    void rejectsLongIdentifiers() {
        assertThatThrownBy(() -> new ScheduledPayoutRequest(
                "p".repeat(51),
                "seller-1",
                PAYOUT_DATE,
                Money.wons(10_000),
                "얼리버드",
                "idempotency-1"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50자 이하");

        assertThatThrownBy(() -> new ScheduledPayoutRequest(
                "payout-1",
                "seller-1",
                PAYOUT_DATE,
                Money.wons(10_000),
                "얼리버드",
                "i".repeat(301)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("300자 이하");
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
