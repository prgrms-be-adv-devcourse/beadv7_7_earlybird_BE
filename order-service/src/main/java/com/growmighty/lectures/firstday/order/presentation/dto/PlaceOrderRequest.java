package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record PlaceOrderRequest(
        @NotNull Long userId,
        @NotEmpty @Valid List<OrderItemRequest> requests,
        @NotNull String receiverName,
        @NotNull String receiverPhone,
        @NotNull String shippingAddress,
        @NotNull String zipCode
) {
    public record OrderItemRequest(@NotNull Long rewardId, @NotNull @Positive Integer quantity) {
    }

    public PlaceOrderCommand toCommand() {
        List<OrderLine> lines = requests.stream()
                .map(r -> new OrderLine(r.rewardId(), r.quantity()))
                .toList();
        return new PlaceOrderCommand(userId, lines, receiverName, receiverPhone, shippingAddress, zipCode);
    }
}
