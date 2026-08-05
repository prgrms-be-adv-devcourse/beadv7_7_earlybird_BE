package com.growmighty.lectures.firstday.settlement.application.port.order;

import java.util.HashSet;
import java.util.List;

public record ProjectOrders(
        Long projectId,
        List<OrderPayment> orders
) {

    public ProjectOrders {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        orders = List.copyOf(orders);
        if (new HashSet<>(orders.stream().map(OrderPayment::orderId).toList()).size()
                != orders.size()) {
            throw new IllegalArgumentException("주문 식별자는 중복될 수 없습니다.");
        }
    }
}
