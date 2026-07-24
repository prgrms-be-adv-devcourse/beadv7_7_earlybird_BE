package com.growmighty.lectures.firstday.cart.presentation.dto;

import com.growmighty.lectures.firstday.cart.application.dto.UpdateCartItemQuantitiesCommand;

import java.util.List;

public record UpdateCartItemsRequest(Long projectId, List<UpdateCartItemQuantityRequest> items) {
    public UpdateCartItemQuantitiesCommand toCommand(Long userId) {
        return new UpdateCartItemQuantitiesCommand(
                userId,
                projectId,
                items == null
                        ? null
                        : items.stream()
                                .map(item -> item == null
                                        ? null
                                        : new UpdateCartItemQuantitiesCommand.Item(item.rewardId(), item.quantity()))
                                .toList());
    }
}
