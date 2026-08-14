package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPaidCompletionService {
    private final OrderPaidPersistenceService persistenceService;
    private final OrderPaymentStatusOutboxRecoveryService paymentStatusRecoveryService;
    private final CartCleanupRecoveryService cleanupRecoveryService;
    private final FundedAmountSynchronizationService fundedAmountSynchronizationService;

    public Order persistAndCleanup(Order order) {
        Order savedOrder = persistenceService.savePaidWithCleanup(order);
        paymentStatusRecoveryService.publishImmediately(savedOrder.getId(), OrderStatus.PAID);
        cleanupRecoveryService.cleanupImmediately(savedOrder.getId());
        fundedAmountSynchronizationService.synchronize(savedOrder.getProjectId());
        return savedOrder;
    }
}
