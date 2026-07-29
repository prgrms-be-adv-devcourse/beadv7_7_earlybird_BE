package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderInspectionView;

import java.math.BigDecimal;
import java.util.List;

public record OrderInspectionResponse(
        Long orderId,
        Long userId,
        String orderStatus,
        BigDecimal itemsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        BigDecimal paymentAmount,
        Long projectId,
        List<ItemResponse> items
) {
    public record ItemResponse(
            Long orderItemId,
            Long projectId,
            Long rewardId,
            String rewardName,
            int quantity,
            BigDecimal unitAmount,
            BigDecimal amount
    ) {
        static ItemResponse from(OrderInspectionView.Item item) {
            return new ItemResponse(
                    item.orderItemId(),
                    item.projectId(),
                    item.rewardId(),
                    item.rewardName(),
                    item.quantity(),
                    item.unitAmount(),
                    item.amount());
        }
    }

    public static OrderInspectionResponse from(OrderInspectionView view) {
        List<ItemResponse> items = view.items().stream()
                .map(ItemResponse::from)
                .toList();
        return new OrderInspectionResponse(
                view.orderId(),
                view.userId(),
                view.orderStatus().name(),
                view.itemsAmount(),
                view.shippingFee(),
                view.totalAmount(),
                view.paymentAmount(),
                view.projectId(),
                items);
    }
}
