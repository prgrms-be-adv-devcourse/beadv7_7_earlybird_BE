package com.growmighty.lectures.firstday.order.application.dto;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderResult(
        Long id,
        OrderStatus status,
        BigDecimal itemsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        String zipCode,
        List<Item> orderItems
) {
    public record Item(
            Long id,
            String name,
            BigDecimal price,
            Long projectId,
            Long rewardId,
            Integer quantity,
            BigDecimal subtotal
    ) {
        public static Item from(OrderItem item) {
            return new Item(
                    item.getId(),
                    item.getName(),
                    item.getPrice().getValue(),
                    item.getProjectId(),
                    item.getRewardId(),
                    item.getQuantity(),
                    item.subtotal().getValue());
        }
    }

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
                order.getZipCode(),
                order.getItems().stream()
                        .map(Item::from)
                        .toList());
    }
}
