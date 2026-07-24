package com.growmighty.lectures.firstday.order.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlaceOrderCommand(
        UUID orderId,
        Long userId,
        List<OrderLine> lines,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        String zipCode,
        BigDecimal expectedItemsAmount,
        BigDecimal expectedTotalAmount
) {
}
