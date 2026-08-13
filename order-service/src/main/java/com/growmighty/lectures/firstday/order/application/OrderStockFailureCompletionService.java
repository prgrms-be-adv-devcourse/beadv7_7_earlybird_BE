package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderStockFailureCompletionService {
    private final OrderStockFailurePersistenceService persistenceService;
    private final CartCleanupRecoveryService cleanupRecoveryService;

    public Order persistAndCleanup(Order order, Long invalidRewardId) {
        Order savedOrder = persistenceService.saveWithInvalidRewardCleanup(order, invalidRewardId);
        cleanupRecoveryService.cleanupImmediately(savedOrder.getId());
        return savedOrder;
    }
}
