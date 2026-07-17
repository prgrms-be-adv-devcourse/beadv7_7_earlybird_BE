package com.growmighty.lectures.firstday.order.application.dto;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderInspectionView(
        Long orderId,
        Long userId,
        OrderStatus orderStatus,
        BigDecimal itemsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        BigDecimal paymentAmount,
        Long paymentId,
        List<Item> items
) {
    public record Item(
            Long orderItemId,
            Long projectId,
            Long rewardId,
            String rewardName,
            int quantity,
            BigDecimal unitAmount,
            BigDecimal amount
    ) {
        static Item from(OrderItem item) {
            return new Item(
                    item.getId(),
                    item.getProjectId(),
                    item.getRewardId(),
                    item.getName(),
                    item.getQuantity(),
                    item.getPrice().getValue(),
                    item.subtotal().getValue());
        }
    }

    public static OrderInspectionView from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(Item::from)
                .toList();
        BigDecimal totalAmount = order.getTotalAmount().getValue();
        return new OrderInspectionView(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getItemsAmount().getValue(),
                order.getShippingFee().getValue(),
                totalAmount,
                totalAmount,
                order.getPaymentId(),
                items);
    }
}
