package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCancellationPersistenceService {
    private final OrderRepository orderRepository;
    private final OrderPaymentStatusOutboxWriter paymentStatusOutboxWriter;

    @Transactional
    public Order saveCancelledWithPaymentStatus(Order order) {
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancellation Outbox can only be created for a cancelled Order. orderId="
                    + order.getId());
        }
        Order savedOrder = orderRepository.save(order);
        paymentStatusOutboxWriter.saveIfAbsent(savedOrder);
        return savedOrder;
    }
}
