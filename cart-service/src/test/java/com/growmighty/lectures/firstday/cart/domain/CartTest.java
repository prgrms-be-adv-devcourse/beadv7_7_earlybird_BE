package com.growmighty.lectures.firstday.cart.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    @Test
    @DisplayName("같은 상품을 다시 담으면 항목이 늘지 않고 수량이 합산된다")
    void addItem_mergesSameProduct() {
        Cart cart = Cart.create(1L);

        cart.addItem(10L, 2);
        cart.addItem(10L, 3);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("다른 상품은 별도 항목으로 담긴다")
    void addItem_distinctProducts() {
        Cart cart = Cart.create(1L);

        cart.addItem(10L, 1);
        cart.addItem(20L, 1);

        assertThat(cart.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("한 항목 최대 수량(99)을 초과하면 예외가 발생한다")
    void addItem_exceedsMaxQuantity_throws() {
        Cart cart = Cart.create(1L);
        assertThatThrownBy(() -> cart.addItem(10L, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("수량을 변경하고 항목을 제거할 수 있다")
    void changeQuantity_and_removeItem() {
        Cart cart = Cart.create(1L);
        cart.addItem(10L, 1);

        cart.changeQuantity(10L, 7);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(7);

        cart.removeItem(10L);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("장바구니에 없는 상품을 변경/제거하면 예외가 발생한다")
    void operateMissingItem_throws() {
        Cart cart = Cart.create(1L);
        assertThatThrownBy(() -> cart.changeQuantity(10L, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.removeItem(10L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("담을 수 있는 상품 종류 최대치를 초과하면 예외가 발생한다")
    void addItem_exceedsMaxDistinct_throws() {
        Cart cart = Cart.create(1L);
        for (long productId = 1; productId <= Cart.MAX_DISTINCT_ITEMS; productId++) {
            cart.addItem(productId, 1);
        }
        assertThatThrownBy(() -> cart.addItem(999L, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("장바구니를 비우면 모든 항목이 제거된다")
    void clear_removesAll() {
        Cart cart = Cart.create(1L);
        cart.addItem(10L, 1);
        cart.addItem(20L, 1);

        cart.clear();

        assertThat(cart.getItems()).isEmpty();
    }
}
