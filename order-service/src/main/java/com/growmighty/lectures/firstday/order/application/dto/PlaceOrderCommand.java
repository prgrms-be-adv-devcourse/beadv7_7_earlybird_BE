package com.growmighty.lectures.firstday.order.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlaceOrderCommand(
        Long userId,
        Long projectId,
        List<OrderLine> lines,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        String zipCode,
        BigDecimal expectedItemsAmount,
        BigDecimal expectedTotalAmount
) {
}
