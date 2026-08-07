// TODO(settlement-plan): Verify creatorId-to-destination eligibility and prohibit payout without an eligible Toss seller.
package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreatorPayoutProfileTest {

    @Test
    @DisplayName("셀러 등록 전의 창작자는 지급 대상이 될 수 없다")
    void creatorCannotReceivePayoutBeforeSellerRegistration() {
        CreatorPayoutProfile profile = CreatorPayoutProfile.awaitingRegistration(10L);

        assertThat(profile.canReceivePayout()).isFalse();
    }

    @Test
    @DisplayName("셀러 등록이 완료되면 외부 상태에 따라 지급할 수 있다")
    void creatorCanReceivePayoutAfterEligibleRegistration() {
        CreatorPayoutProfile profile = CreatorPayoutProfile.awaitingRegistration(10L);

        profile.completeRegistration(
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );

        assertThat(profile.canReceivePayout()).isTrue();
    }

    @Test
    @DisplayName("지급 가능한 창작자는 지급 대상이 될 수 있다")
    void eligibleCreatorCanReceivePayout() {
        CreatorPayoutProfile profile = CreatorPayoutProfile.registered(
                10L,
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );

        assertThat(profile.canReceivePayout()).isTrue();
    }

    @Test
    @DisplayName("창작자의 현재 지급 대상을 스냅샷으로 만든다")
    void createsPayoutDestinationSnapshot() {
        CreatorPayoutProfile profile = CreatorPayoutProfile.registered(
                10L,
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );

        PayoutDestinationSnapshot snapshot = profile.snapshotDestination();

        assertThat(snapshot).isEqualTo(
                PayoutDestinationSnapshot.of(10L, "seller-10", "088", "********1234")
        );
    }
}
