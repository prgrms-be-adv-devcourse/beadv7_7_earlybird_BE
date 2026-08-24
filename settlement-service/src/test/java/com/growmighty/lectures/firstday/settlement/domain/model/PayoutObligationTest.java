package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayoutObligationTest {

    @Test
    @DisplayName("지급 의무가 정산의 창작자와 지급 금액을 고정하고 지급 시도를 소유한다")
    void ownsPayoutLifecycleForSettlement() {
        PayoutObligation payoutObligation = payoutObligation();
        PayoutAttempt first = payoutObligation.startAttempt("ref-1", "key-1", LocalDateTime.of(2026, 8, 3, 9, 0));
        payoutObligation.failAttempt(first, "toss-1", "TEMPORARY", LocalDateTime.of(2026, 8, 3, 9, 1), true);
        PayoutAttempt retry = payoutObligation.startAttempt("ref-2", "key-2", LocalDateTime.of(2026, 8, 3, 9, 2));

        assertThat(payoutObligation.creatorId()).isEqualTo(10L);
        assertThat(payoutObligation.payoutAmount()).isEqualTo(Money.wons(91_200));
        assertThat(retry.sequence()).isEqualTo(2);
        assertThat(payoutObligation.status()).isEqualTo(PayoutStatus.PROCESSING);
    }

    @Test
    @DisplayName("재시도 가능한 실패도 네 번째 시도 뒤에는 지급 조치 필요로 전이한다")
    void limitsRetryableFailures() {
        PayoutObligation payoutObligation = payoutObligation();

        for (int sequence = 1; sequence <= 4; sequence++) {
            PayoutAttempt attempt = payoutObligation.startAttempt(
                    "ref-" + sequence,
                    "key-" + sequence,
                    LocalDateTime.of(2026, 8, 3, 9, sequence)
            );
            payoutObligation.failAttempt(
                    attempt,
                    "toss-" + sequence,
                    "TEMPORARY",
                    LocalDateTime.of(2026, 8, 3, 10, sequence),
                    true
            );
        }

        assertThat(payoutObligation.status()).isEqualTo(PayoutStatus.ACTION_REQUIRED);
    }

    @Test
    @DisplayName("지급 프로필의 창작자가 정산과 다르면 지급 의무 생성을 거부한다")
    void rejectsDifferentCreatorProfile() {
        ProjectSettlement settlement = ProjectSettlement.confirm(
                1L, 10L, List.of(Money.wons(100_000)), LocalDateTime.of(2026, 7, 22, 10, 0));

        assertThatThrownBy(() -> PayoutObligation.schedule(settlement, profile(11L), LocalDate.of(2026, 8, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PayoutObligation payoutObligation() {
        return PayoutObligation.schedule(ProjectSettlement.confirm(
                        1L, 10L, List.of(Money.wons(100_000)), LocalDateTime.of(2026, 7, 22, 10, 0)),
                profile(10L), LocalDate.of(2026, 8, 3));
    }

    private static CreatorPayoutProfile profile(Long creatorId) {
        return CreatorPayoutProfile.registered(creatorId, "seller-" + creatorId, CreatorPayoutStatus.PAYOUT_READY);
    }
}
