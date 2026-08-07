// TODO(settlement-plan): Verify one successful attempt, unknown-result blocking, and safe retry transitions.
package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayoutObligationTest {

    @Test
    @DisplayName("예약된 지급 의무에서 첫 지급 시도를 시작한다")
    void startsFirstAttemptFromScheduledObligation() {
        PayoutObligation obligation = PayoutObligation.schedule(
                100L,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3)
        );

        PayoutAttempt attempt = obligation.startAttempt(
                "ref-payout-100-1",
                "idempotency-100-1",
                LocalDateTime.of(2026, 8, 3, 9, 0)
        );

        assertThat(attempt.sequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("최종 실패가 확인되면 새 지급 시도를 시작한다")
    void startsNewAttemptAfterConfirmedFailure() {
        PayoutObligation obligation = PayoutObligation.schedule(
                100L,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3)
        );
        PayoutAttempt firstAttempt = obligation.startAttempt(
                "ref-payout-100-1",
                "idempotency-100-1",
                LocalDateTime.of(2026, 8, 3, 9, 0)
        );
        obligation.failAttempt(
                firstAttempt,
                "toss-payout-1",
                "TEMPORARY_BANK_ERROR",
                LocalDateTime.of(2026, 8, 3, 9, 1),
                true
        );

        PayoutAttempt retriedAttempt = obligation.startAttempt(
                "ref-payout-100-2",
                "idempotency-100-2",
                LocalDateTime.of(2026, 8, 3, 9, 5)
        );

        assertThat(retriedAttempt.sequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("지급에 성공하면 새 지급 시도를 허용하지 않는다")
    void doesNotAllowAnotherAttemptAfterSuccess() {
        PayoutObligation obligation = PayoutObligation.schedule(
                100L,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3)
        );
        PayoutAttempt attempt = obligation.startAttempt(
                "ref-payout-100-1",
                "idempotency-100-1",
                LocalDateTime.of(2026, 8, 3, 9, 0)
        );
        obligation.completeAttempt(
                attempt,
                "toss-payout-1",
                LocalDateTime.of(2026, 8, 3, 9, 1)
        );

        assertThatThrownBy(() -> obligation.startAttempt(
                "ref-payout-100-2",
                "idempotency-100-2",
                LocalDateTime.of(2026, 8, 3, 9, 5)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("지급 결과가 불명확하면 새 지급 시도를 허용하지 않는다")
    void doesNotAllowAnotherAttemptWhileResultIsUnknown() {
        PayoutObligation obligation = PayoutObligation.schedule(
                100L,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3)
        );
        PayoutAttempt attempt = obligation.startAttempt(
                "ref-payout-100-1",
                "idempotency-100-1",
                LocalDateTime.of(2026, 8, 3, 9, 0)
        );
        obligation.markAttemptUnknown(attempt);

        assertThatThrownBy(() -> obligation.startAttempt(
                "ref-payout-100-2",
                "idempotency-100-2",
                LocalDateTime.of(2026, 8, 3, 9, 5)
        )).isInstanceOf(IllegalStateException.class);
    }
}
