package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull UUID orderId,
        @NotNull Long userId,
        @NotEmpty @Valid List<OrderItemRequest> requests,
        @NotNull String receiverName,
        @NotNull String receiverPhone,
        @NotNull String shippingAddress,
        @NotNull String zipCode,
        @NotNull BigDecimal expectedItemsAmount,
        @NotNull BigDecimal expectedTotalAmount
) {
    public record OrderItemRequest(
            @NotNull Long rewardId,
            @NotNull @Positive Integer quantity,
            @NotNull BigDecimal expectedUnitPrice
    ) {
    }

    public PlaceOrderCommand toCommand() {
        List<OrderLine> lines = requests.stream()
                .map(r -> new OrderLine(r.rewardId(), r.quantity(), r.expectedUnitPrice()))
                .toList();
        return new PlaceOrderCommand(orderId, userId, lines, receiverName, receiverPhone, shippingAddress, zipCode,
                expectedItemsAmount, expectedTotalAmount);
    }
}
