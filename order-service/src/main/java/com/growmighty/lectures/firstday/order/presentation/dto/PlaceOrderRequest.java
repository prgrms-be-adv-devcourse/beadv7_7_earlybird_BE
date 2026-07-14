package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import lombok.NonNull;

import java.util.List;

public record PlaceOrderRequest(
        @NonNull Long userId,
        @NonNull List<OrderItemRequest> requests
) {
    public record OrderItemRequest(@NonNull Long productId, @NonNull Integer quantity) {
    }

    public PlaceOrderCommand toCommand() {
        List<OrderLine> lines = requests.stream()
                .map(r -> new OrderLine(r.productId(), r.quantity()))
                .toList();
        return new PlaceOrderCommand(userId, lines);
    }
}
