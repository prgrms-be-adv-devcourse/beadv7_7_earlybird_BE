package com.growmighty.lectures.firstday.project.reward.domain;

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
    @DisplayName("총 수량이 null이면 무제한 리워드로 등록되고 항상 후원 가능하다")
    void register_nullTotalQuantity_unlimited() {
        Reward reward = Reward.register(1L, "무제한 후원", "설명", BigDecimal.valueOf(1_000), null);
        assertThat(reward.getTotalQuantity()).isNull();
        assertThat(reward.getRemainingQuantity()).isNull();
        assertThat(reward.isOrderable()).isTrue();
    }

    @Test
    @DisplayName("무제한 리워드는 재고 차감/복원 검증을 건너뛴다")
    void unlimitedReward_skipsStockValidation() {
        Reward reward = Reward.register(1L, "무제한 후원", "설명", BigDecimal.valueOf(1_000), null);

        reward.decreaseStock(1_000_000);
        assertThat(reward.getRemainingQuantity()).isNull();
        assertThat(reward.isOrderable()).isTrue();

        reward.restoreStock(1_000_000);
        assertThat(reward.getRemainingQuantity()).isNull();
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

    @Test
    @DisplayName("무제한 리워드는 수량 추가 대상이 아니다")
    void increaseQuantity_unlimitedReward_throws() {
        Reward reward = Reward.register(1L, "무제한 후원", "설명", BigDecimal.valueOf(1_000), null);
        assertThatThrownBy(() -> reward.increaseQuantity(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("수량 축소 시 총 수량과 잔여 수량이 함께 줄어든다")
    void decreaseQuantity_reducesBoth() {
        Reward reward = reward(10);
        reward.decreaseStock(3);

        reward.decreaseQuantity(4);
        assertThat(reward.getTotalQuantity()).isEqualTo(6);
        assertThat(reward.getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("이미 판매된 수량보다 적게는 축소할 수 없다")
    void decreaseQuantity_belowSold_throws() {
        Reward reward = reward(10);
        reward.decreaseStock(8);

        assertThatThrownBy(() -> reward.decreaseQuantity(3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("무제한 리워드는 수량 축소 대상이 아니다")
    void decreaseQuantity_unlimitedReward_throws() {
        Reward reward = Reward.register(1L, "무제한 후원", "설명", BigDecimal.valueOf(1_000), null);
        assertThatThrownBy(() -> reward.decreaseQuantity(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("공개 전에는 이름/설명/가격/총수량을 자유롭게 수정할 수 있고, null 필드는 바뀌지 않는다")
    void updateBeforePublish_changesOnlyNonNullFields() {
        Reward reward = reward(10);

        reward.updateBeforePublish("새 이름", null, BigDecimal.valueOf(35_000), null);
        assertThat(reward.getName()).isEqualTo("새 이름");
        assertThat(reward.getDescription()).isEqualTo("설명");
        assertThat(reward.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(35_000));
        assertThat(reward.getTotalQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("공개 전 총수량을 바꾸면 잔여 수량도 그대로 맞춰진다")
    void updateBeforePublish_totalQuantity_syncsRemaining() {
        Reward reward = reward(10);

        reward.updateBeforePublish(null, null, null, 20);
        assertThat(reward.getTotalQuantity()).isEqualTo(20);
        assertThat(reward.getRemainingQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("비활성화하면 재고가 남아있어도 후원 불가능해진다")
    void deactivate_notOrderable() {
        Reward reward = reward(10);
        assertThat(reward.isOrderable()).isTrue();

        reward.deactivate();
        assertThat(reward.isActive()).isFalse();
        assertThat(reward.isOrderable()).isFalse();
    }

    @Test
    @DisplayName("비활성화된 리워드는 재고가 남아있어도 차감(주문)할 수 없다")
    void decreaseStock_deactivated_throws() {
        Reward reward = reward(10);
        reward.deactivate();

        assertThatThrownBy(() -> reward.decreaseStock(1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("무제한 리워드도 비활성화되면 차감할 수 없다")
    void decreaseStock_deactivated_unlimitedReward_throws() {
        Reward reward = Reward.register(1L, "무제한 후원", "설명", BigDecimal.valueOf(1_000), null);
        reward.deactivate();

        assertThatThrownBy(() -> reward.decreaseStock(1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("공개 전 총수량을 바꿔도 이미 차감된 수량은 보존된다")
    void updateBeforePublish_totalQuantity_preservesAlreadySoldAmount() {
        Reward reward = reward(10);
        reward.decreaseStock(3);
        assertThat(reward.getRemainingQuantity()).isEqualTo(7);

        reward.updateBeforePublish(null, null, null, 20);
        assertThat(reward.getTotalQuantity()).isEqualTo(20);
        assertThat(reward.getRemainingQuantity()).isEqualTo(17);
    }

    @Test
    @DisplayName("이미 판매된 수량보다 적은 총수량으로는 변경할 수 없다")
    void updateBeforePublish_totalQuantity_belowSold_throws() {
        Reward reward = reward(10);
        reward.decreaseStock(8);

        assertThatThrownBy(() -> reward.updateBeforePublish(null, null, null, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("공개 전 이름을 빈 값으로 변경할 수 없다")
    void updateBeforePublish_blankName_throws() {
        Reward reward = reward(10);

        assertThatThrownBy(() -> reward.updateBeforePublish("   ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reward.getName()).isEqualTo("[얼리버드] 노트커버 1개");
    }
}
