package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCancellationCompletionService {
    private final OrderPaymentStatusOutboxRecoveryService paymentStatusRecoveryService;
    private final FundedAmountSynchronizationService fundedAmountSynchronizationService;

    public void complete(Order cancelledOrder) {
        paymentStatusRecoveryService.publishImmediately(cancelledOrder.getId(), cancelledOrder.getStatus());
        fundedAmountSynchronizationService.synchronize(cancelledOrder.getProjectId());
    }
}
