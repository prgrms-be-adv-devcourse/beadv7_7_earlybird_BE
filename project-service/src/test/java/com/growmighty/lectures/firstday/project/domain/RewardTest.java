package com.growmighty.lectures.firstday.project.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardTest {

    private Reward reward(int quantity) {
        return Reward.register(1L, "[얼리버드] 노트커버 1개", "설명", BigDecimal.valueOf(29_000), quantity);
    }

    @Test
    @DisplayName("리워드는 총 수량과 같은 잔여 수량으로 등록된다")
    void register_remainingEqualsTotal() {
        Reward reward = reward(100);
        assertThat(reward.getTotalQuantity()).isEqualTo(100);
        assertThat(reward.getRemainingQuantity()).isEqualTo(100);
        assertThat(reward.isOrderable()).isTrue();
    }

    @Test
    @DisplayName("가격이 0 이하이거나 수량이 음수면 등록할 수 없다")
    void register_invalidValues_throw() {
        assertThatThrownBy(() -> Reward.register(1L, "x", "d", BigDecimal.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reward.register(1L, "x", "d", BigDecimal.valueOf(1000), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재고를 차감하면 잔여 수량이 줄고, 0이 되면 후원 불가능해진다")
    void decreaseStock_toZero_notOrderable() {
        Reward reward = reward(5);

        reward.decreaseStock(2);
        assertThat(reward.getRemainingQuantity()).isEqualTo(3);
        assertThat(reward.isOrderable()).isTrue();

        reward.decreaseStock(3);
        assertThat(reward.getRemainingQuantity()).isZero();
        assertThat(reward.isOrderable()).isFalse();
    }

    @Test
    @DisplayName("잔여 수량보다 많이 차감하면 예외가 발생한다")
    void decreaseStock_insufficient_throws() {
        Reward reward = reward(1);
        assertThatThrownBy(() -> reward.decreaseStock(2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("재고를 복원하면 품절 리워드가 다시 후원 가능해진다")
    void restoreStock_backToOrderable() {
        Reward reward = reward(1);
        reward.decreaseStock(1);
        assertThat(reward.isOrderable()).isFalse();

        reward.restoreStock(1);
        assertThat(reward.getRemainingQuantity()).isEqualTo(1);
        assertThat(reward.isOrderable()).isTrue();
    }

    @Test
    @DisplayName("총 수량을 넘겨 복원할 수 없다")
    void restoreStock_overTotal_throws() {
        Reward reward = reward(1);
        assertThatThrownBy(() -> reward.restoreStock(1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("수량 추가 시 총 수량과 잔여 수량이 함께 늘어난다 (축소는 불가 정책)")
    void increaseQuantity_addsBoth() {
        Reward reward = reward(10);
        reward.decreaseStock(4);

        reward.increaseQuantity(5);
        assertThat(reward.getTotalQuantity()).isEqualTo(15);
        assertThat(reward.getRemainingQuantity()).isEqualTo(11);

        assertThatThrownBy(() -> reward.increaseQuantity(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
