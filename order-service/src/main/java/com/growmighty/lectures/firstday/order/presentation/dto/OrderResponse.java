package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderResult;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String status,
        BigDecimal itemsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        String zipCode
) {
    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(
                result.id(),
                result.status().name(),
                result.itemsAmount(),
                result.shippingFee(),
                result.totalAmount(),
                result.receiverName(),
                result.receiverPhone(),
                result.shippingAddress(),
                result.zipCode());
    }
}
