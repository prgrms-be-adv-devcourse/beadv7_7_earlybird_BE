package com.growmighty.lectures.firstday.cart.application.dto;

import java.util.List;

public record UpdateCartItemQuantitiesCommand(Long userId, Long projectId, List<Item> items) {
    public record Item(Long rewardId, Integer quantity) {
    }
}
