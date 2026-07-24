package com.growmighty.lectures.firstday.cart.presentation.dto;

import com.growmighty.lectures.firstday.cart.application.dto.AddCartItemsCommand;

import java.util.List;

public record AddCartItemsRequest(Long projectId, List<AddCartItemRequest> items) {
    public AddCartItemsCommand toCommand(Long userId) {
        return new AddCartItemsCommand(
                userId,
                projectId,
                items == null
                        ? null
                        : items.stream()
                                .map(item -> item == null
                                        ? null
                                        : new AddCartItemsCommand.Item(item.rewardId(), item.quantity()))
                                .toList());
    }
}
