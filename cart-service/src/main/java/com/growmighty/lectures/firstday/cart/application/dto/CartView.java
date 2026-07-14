package com.growmighty.lectures.firstday.cart.application.dto;

import com.growmighty.lectures.firstday.cart.domain.Cart;

import java.util.List;

public record CartView(Long cartId, Long userId, List<Line> items) {
    public record Line(Long productId, int quantity) {
    }

    public static CartView from(Cart cart) {
        List<Line> lines = cart.getItems().stream()
                .map(item -> new Line(item.getProductId(), item.getQuantity()))
                .toList();
        return new CartView(cart.getId(), cart.getUserId(), lines);
    }
}
