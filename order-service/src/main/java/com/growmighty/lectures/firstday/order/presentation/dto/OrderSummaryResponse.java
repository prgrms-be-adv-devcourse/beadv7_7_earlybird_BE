package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderResult;

import java.math.BigDecimal;

public record OrderSummaryResponse(
        Long id,
        String status,
        BigDecimal itemsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        String zipCode
) {
    public static OrderSummaryResponse from(OrderResult result) {
        return new OrderSummaryResponse(
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
