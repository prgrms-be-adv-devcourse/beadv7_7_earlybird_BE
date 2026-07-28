package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record PlaceOrderRequest(
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

    public PlaceOrderCommand toCommand(String idempotencyKey) {
        return toCommand(userId, idempotencyKey);
    }

    public PlaceOrderCommand toCommand(Long userId, String idempotencyKey) {
        List<OrderLine> lines = requests.stream()
                .map(r -> new OrderLine(r.rewardId(), r.quantity(), r.expectedUnitPrice()))
                .toList();
        return new PlaceOrderCommand(idempotencyKey, userId, lines, receiverName, receiverPhone, shippingAddress, zipCode,
                expectedItemsAmount, expectedTotalAmount);
    }
}
