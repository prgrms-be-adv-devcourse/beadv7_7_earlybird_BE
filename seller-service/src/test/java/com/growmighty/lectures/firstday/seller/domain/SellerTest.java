package com.growmighty.lectures.firstday.seller.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerTest {

    @Test
    @DisplayName("입점하면 ACTIVE 상태이고 판매가 가능하다")
    void apply_isActive() {
        Seller seller = Seller.apply(1L, "그로마이티 가구");

        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.canSell()).isTrue();
    }

    @Test
    @DisplayName("상호명이 비어 있으면 입점할 수 없다")
    void apply_blankBusinessName_throws() {
        assertThatThrownBy(() -> Seller.apply(1L, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정지하면 판매가 불가하고, 재개하면 다시 가능하다")
    void suspend_and_activate() {
        Seller seller = Seller.apply(1L, "그로마이티 가구");

        seller.suspend();
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(seller.canSell()).isFalse();

        seller.activate();
        assertThat(seller.canSell()).isTrue();
    }
}
