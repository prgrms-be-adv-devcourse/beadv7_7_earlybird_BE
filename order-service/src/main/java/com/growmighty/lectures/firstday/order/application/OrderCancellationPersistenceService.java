package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class OrderCancellationPersistenceService {
    private final OrderRepository orderRepository;
    private final OrderPaymentStatusOutboxWriter paymentStatusOutboxWriter;
    private final OrderStockHandler stockHandler;

    public OrderCancellationPersistenceService(OrderRepository orderRepository,
                                               OrderPaymentStatusOutboxWriter paymentStatusOutboxWriter,
                                               OrderStockHandler stockHandler) {
        this.orderRepository = orderRepository;
        this.paymentStatusOutboxWriter = paymentStatusOutboxWriter;
        this.stockHandler = stockHandler;
    }

    @Transactional
    public Order finalizeCancellation(Long orderId, String pgOrderId) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
        if (pgOrderId != null && order.getPgOrderId() != null
                && !Objects.equals(order.getPgOrderId(), pgOrderId)) {
            throw new IllegalStateException("Payment cancellation PG order ID mismatch. orderId=" + orderId);
        }
        order.assignPgOrderId(pgOrderId);

        if (!order.isCancelled()) {
            order.validateCancellationAllowed();
            stockHandler.releaseStock(order);
            order.cancel();
        }

        Order savedOrder = orderRepository.save(order);
        if (paymentStatusOutboxWriter != null) {
            paymentStatusOutboxWriter.saveIfAbsent(savedOrder);
        }
        return savedOrder;
    }
}
