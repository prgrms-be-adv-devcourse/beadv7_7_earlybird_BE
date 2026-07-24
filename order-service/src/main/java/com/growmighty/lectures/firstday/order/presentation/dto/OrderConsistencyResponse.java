package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderConsistencyView;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderConsistencyResponse(
        UUID orderId,
        BigDecimal storedTotal,
        BigDecimal recalculatedTotal,
        boolean consistent
) {
    public static OrderConsistencyResponse from(OrderConsistencyView view) {
        return new OrderConsistencyResponse(
                view.orderId(), view.storedTotal(), view.recalculatedTotal(), view.consistent());
    }
}
