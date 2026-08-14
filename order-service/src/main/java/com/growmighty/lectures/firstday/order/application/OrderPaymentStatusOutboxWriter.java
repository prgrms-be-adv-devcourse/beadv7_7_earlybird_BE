package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutbox;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
class OrderPaymentStatusOutboxWriter {
    private final OrderPaymentStatusOutboxRepository outboxRepository;

    void saveIfAbsent(Order order) {
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PAID && status != OrderStatus.CANCELLED) {
            throw new IllegalStateException("Payment status Outbox requires a successful final status. orderId="
                    + order.getId());
        }
        if (outboxRepository.existsByOrderIdAndOrderStatus(order.getId(), status)) {
            return;
        }
        outboxRepository.save(OrderPaymentStatusOutbox.pending(
                order.getId(),
                order.getPgOrderId(),
                order.getProjectId(),
                order.getTotalAmount().getValue(),
                status,
                Instant.now()
        ));
    }
}
