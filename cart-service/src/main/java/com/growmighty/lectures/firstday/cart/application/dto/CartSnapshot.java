package com.growmighty.lectures.firstday.cart.application.dto;

import com.growmighty.lectures.firstday.cart.domain.Cart;

import java.util.List;

public record CartSnapshot(Long userId, List<Item> items) {
    public record Item(Long rewardId, int quantity) {
    }

    public static CartSnapshot from(Cart cart) {
        return new CartSnapshot(cart.getUserId(), cart.getItems().stream()
                .map(item -> new Item(item.getRewardId(), item.getQuantity()))
                .toList());
    }

    public static CartSnapshot empty(Long userId) {
        return new CartSnapshot(userId, List.of());
    }
}
