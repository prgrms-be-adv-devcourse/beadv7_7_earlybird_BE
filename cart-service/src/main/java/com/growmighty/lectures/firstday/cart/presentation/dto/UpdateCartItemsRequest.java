package com.growmighty.lectures.firstday.cart.presentation.dto;

import com.growmighty.lectures.firstday.cart.application.dto.UpdateCartItemQuantitiesCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateCartItemsRequest(@NotNull Long projectId, @NotEmpty @Valid List<UpdateCartItemQuantityRequest> items) {
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
