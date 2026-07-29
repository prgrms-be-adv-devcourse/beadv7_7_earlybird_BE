package com.growmighty.lectures.firstday.cart.presentation.dto;

import com.growmighty.lectures.firstday.cart.application.dto.AddCartItemsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AddCartItemsRequest(@NotNull Long projectId, @NotEmpty @Valid List<AddCartItemRequest> items) {
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
