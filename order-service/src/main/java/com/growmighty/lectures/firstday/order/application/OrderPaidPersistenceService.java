package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.CartCleanupOutbox;
import com.growmighty.lectures.firstday.order.domain.CartCleanupOutboxRepository;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderPaidPersistenceService {
    private final OrderRepository orderRepository;
    private final CartCleanupOutboxRepository outboxRepository;

    @Transactional
    public Order savePaidWithCleanup(Order order) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Cart cleanup Outbox can only be created for a paid Order. orderId="
                    + order.getId());
        }
        Order savedOrder = orderRepository.save(order);
        if (!outboxRepository.existsByOrderId(savedOrder.getId())) {
            outboxRepository.save(CartCleanupOutbox.pending(
                    savedOrder.getId(), savedOrder.getUserId(), savedOrder.getItems().stream()
                            .map(OrderItem::getRewardId)
                            .toList(), LocalDateTime.now()));
        }
        return savedOrder;
    }
}
