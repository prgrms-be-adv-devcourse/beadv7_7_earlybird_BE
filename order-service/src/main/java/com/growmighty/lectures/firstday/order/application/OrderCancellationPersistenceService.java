package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCancellationPersistenceService {
    private final OrderCancellationTransactionService transactions;
    private final OrderStockHandler stockHandler;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order finalizeCancellation(Long orderId, String pgOrderId) {
        Order order = transactions.prepareCancellation(orderId, pgOrderId);
        return restoreStockAndComplete(order);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order recoverCancellationCompensation(Long orderId) {
        Order order = transactions.loadCancellationCompensation(orderId);
        return restoreStockAndComplete(order);
    }

    private Order restoreStockAndComplete(Order order) {
        if (order.isCancelled()) {
            return order;
        }
        try {
            stockHandler.releaseStock(order);
        } catch (RuntimeException compensationFailure) {
            return order;
        }
        return transactions.completeCancellation(order.getId());
    }
}
