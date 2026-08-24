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

        if (order.isCancelled()) {
            return saveCompletedCancellation(order);
        }
        if (order.isCancellationCompensationPending()) {
            return orderRepository.save(order);
        }

        order.validateCancellationAllowed();
        return restoreStockAndFinalize(order);
    }

    @Transactional
    public Order recoverCancellationCompensation(Long orderId) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
        if (order.isCancelled()) {
            return saveCompletedCancellation(order);
        }
        if (!order.isCancellationCompensationPending()) {
            throw new IllegalStateException("Order cancellation compensation is not pending. orderId=" + orderId);
        }
        return restoreStockAndFinalize(order);
    }

    private Order restoreStockAndFinalize(Order order) {
        try {
            stockHandler.releaseStock(order);
        } catch (RuntimeException compensationFailure) {
            if (!order.isCancellationCompensationPending()) {
                order.markCancellationCompensationPending();
            }
            return orderRepository.save(order);
        }

        if (order.isCancellationCompensationPending()) {
            order.completeCancellationCompensation();
        } else {
            order.cancel();
        }
        return saveCompletedCancellation(order);
    }

    private Order saveCompletedCancellation(Order order) {
        Order savedOrder = orderRepository.save(order);
        if (paymentStatusOutboxWriter != null) {
            paymentStatusOutboxWriter.saveIfAbsent(savedOrder);
        }
        return savedOrder;
    }
}
