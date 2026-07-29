package com.growmighty.lectures.firstday.settlement.application.port;

import java.util.HashSet;
import java.util.List;

public record ProjectOrders(
        Long projectId,
        List<Long> orderIds
) {

    public ProjectOrders {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        orderIds = List.copyOf(orderIds);
        if (orderIds.stream().anyMatch(orderId -> orderId == null || orderId <= 0)) {
            throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
        }
        if (new HashSet<>(orderIds).size() != orderIds.size()) {
            throw new IllegalArgumentException("주문 식별자는 중복될 수 없습니다.");
        }
    }
}
