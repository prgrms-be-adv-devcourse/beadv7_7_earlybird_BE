package com.growmighty.lectures.firstday.order.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        ResultResponse result,
        Long id,
        String status,
        BigDecimal itemsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        String zipCode,
        List<OrderItemResponse> orderItems
) {
    public record ResultResponse(String code, String message) {
    }

    public record OrderItemResponse(
            Long id,
            String name,
            BigDecimal price,
            Long projectId,
            Long rewardId,
            Integer quantity,
            BigDecimal subtotal
    ) {
        public static OrderItemResponse from(OrderResult.Item item) {
            return new OrderItemResponse(
                    item.id(),
                    item.name(),
                    item.price(),
                    item.projectId(),
                    item.rewardId(),
                    item.quantity(),
                    item.subtotal());
        }
    }

    public static OrderResponse created(OrderResult result) {
        return from(result, new ResultResponse("ORDER_CREATED", "Order has been created successfully."));
    }

    public static OrderResponse detail(OrderResult result) {
        return from(result, null);
    }

    private static OrderResponse from(OrderResult result, ResultResponse creationResult) {
        return new OrderResponse(
                creationResult,
                result.id(),
                result.status().name(),
                result.itemsAmount(),
                result.shippingFee(),
                result.totalAmount(),
                result.receiverName(),
                result.receiverPhone(),
                result.shippingAddress(),
                result.zipCode(),
                result.orderItems().stream()
                        .map(OrderItemResponse::from)
                        .toList());
    }
}
