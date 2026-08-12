package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPaidCompletionService {
    private final OrderPaidPersistenceService persistenceService;
    private final CartCleanupRecoveryService cleanupRecoveryService;
    private final FundedAmountSynchronizationService fundedAmountSynchronizationService;

    public Order persistAndCleanup(Order order) {
        Order savedOrder = persistenceService.savePaidWithCleanup(order);
        cleanupRecoveryService.cleanupImmediately(savedOrder.getId());
        fundedAmountSynchronizationService.synchronize(savedOrder.getProjectId());
        return savedOrder;
    }
}
