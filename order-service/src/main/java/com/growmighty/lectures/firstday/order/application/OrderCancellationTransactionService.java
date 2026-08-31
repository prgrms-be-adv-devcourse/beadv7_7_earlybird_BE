package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
class OrderCancellationTransactionService {
    private final OrderRepository orderRepository;
    private final OrderPaymentStatusOutboxWriter paymentStatusOutboxWriter;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order prepareCancellation(Long orderId, String pgOrderId) {
        Order order = findByIdWithItemsForUpdate(orderId);
        validateAndAssignPgOrderId(order, pgOrderId);
        if (order.isCancelled()) {
            return saveCompletedCancellation(order);
        }
        if (order.isCancellationCompensationPending()) {
            return orderRepository.save(order);
        }
        order.validateCancellationAllowed();
        order.markCancellationCompensationPending();
        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order loadCancellationCompensation(Long orderId) {
        Order order = findByIdWithItemsForUpdate(orderId);
        if (order.isCancelled()) {
            return saveCompletedCancellation(order);
        }
        if (!order.isCancellationCompensationPending()) {
            throw new IllegalStateException("Order cancellation compensation is not pending. orderId=" + orderId);
        }
        return order;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order completeCancellation(Long orderId) {
        Order order = findByIdWithItemsForUpdate(orderId);
        if (order.isCancelled()) {
            return saveCompletedCancellation(order);
        }
        if (!order.isCancellationCompensationPending()) {
            throw new IllegalStateException("Order cancellation compensation is not pending. orderId=" + orderId);
        }
        order.getItems().stream()
                .filter(OrderItem::isStockReserved)
                .forEach(OrderItem::markStockRestored);
        order.completeCancellationCompensation();
        return saveCompletedCancellation(order);
    }

    private Order findByIdWithItemsForUpdate(Long orderId) {
        return orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
    }

    private void validateAndAssignPgOrderId(Order order, String pgOrderId) {
        if (pgOrderId != null && order.getPgOrderId() != null
                && !Objects.equals(order.getPgOrderId(), pgOrderId)) {
            throw new IllegalStateException("Payment cancellation PG order ID mismatch. orderId=" + order.getId());
        }
        order.assignPgOrderId(pgOrderId);
    }

    private Order saveCompletedCancellation(Order order) {
        Order savedOrder = orderRepository.save(order);
        if (paymentStatusOutboxWriter != null) {
            paymentStatusOutboxWriter.saveIfAbsent(savedOrder);
        }
        return savedOrder;
    }
}
