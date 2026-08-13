package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.CartCleanupOutbox;
import com.growmighty.lectures.firstday.order.domain.CartCleanupOutboxRepository;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderStockFailurePersistenceService {
    private final OrderRepository orderRepository;
    private final CartCleanupOutboxRepository outboxRepository;

    @Transactional
    public Order saveWithInvalidRewardCleanup(Order order, Long rewardId) {
        if (order.getStatus() != OrderStatus.STOCK_FAILED
                && order.getStatus() != OrderStatus.STOCK_COMPENSATION_PENDING) {
            throw new IllegalStateException("Invalid-reward cleanup Outbox requires a definitive stock failure. orderId="
                    + order.getId());
        }
        Order savedOrder = orderRepository.save(order);
        if (!outboxRepository.existsByOrderId(savedOrder.getId())) {
            outboxRepository.save(CartCleanupOutbox.pendingInvalidReward(
                    savedOrder.getId(), savedOrder.getUserId(), rewardId, LocalDateTime.now()));
        }
        return savedOrder;
    }
}
