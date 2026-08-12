package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.port.CartPort;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class OrderCartHandler {
    private final CartPort cartPort;
    private final OrderRemoteCallExecutor remoteCalls;

    CartPort.CartSnapshot getCart(Long userId) {
        return remoteCalls.execute("cart-get", () -> cartPort.getCart(userId));
    }

    void removeInvalidReward(Long userId, Long rewardId) {
        removeItems(userId, List.of(rewardId), "invalid reward");
    }

    void removeOrderedItems(Order order) {
        removeItems(order.getUserId(), order.getItems().stream()
                .map(OrderItem::getRewardId)
                .toList(), "completed order");
    }

    private void removeItems(Long userId, List<Long> rewardIds, String reason) {
        try {
            remoteCalls.execute("cart-remove-items", () -> cartPort.removeItems(userId, rewardIds));
        } catch (RuntimeException failure) {
            log.warn("cart cleanup deferred after {}. userId={}, rewardIds={}", reason, userId, rewardIds, failure);
        }
    }
}
