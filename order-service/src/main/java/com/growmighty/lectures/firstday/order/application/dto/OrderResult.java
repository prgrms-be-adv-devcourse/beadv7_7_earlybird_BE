package com.growmighty.lectures.firstday.order.application.dto;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResult(
        UUID id,
        OrderStatus status,
        BigDecimal itemsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        String zipCode
) {
    public static OrderResult from(Order order) {
        return new OrderResult(
                order.getId(),
                order.getStatus(),
                order.getItemsAmount().getValue(),
                order.getShippingFee().getValue(),
                order.getTotalAmount().getValue(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getShippingAddress(),
                order.getZipCode());
    }
}
