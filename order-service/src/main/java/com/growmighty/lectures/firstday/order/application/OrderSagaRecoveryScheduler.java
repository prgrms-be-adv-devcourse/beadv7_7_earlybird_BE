package com.growmighty.lectures.firstday.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class OrderSagaRecoveryScheduler {
    private final OrderSagaRecoveryService orderSagaRecoveryService;
    private final CartCleanupRecoveryService cartCleanupRecoveryService;
    private final OrderPaymentStatusOutboxRecoveryService paymentStatusOutboxRecoveryService;

    @Scheduled(fixedDelayString = "${order.saga.recovery-delay-ms:30000}")
    public void recover() {
        orderSagaRecoveryService.recoverPendingOrders();
    }

    @Scheduled(fixedDelayString = "${order.cart-cleanup.recovery-delay-ms:30000}")
    public void recoverCartCleanup() {
        cartCleanupRecoveryService.recoverPendingCleanups();
    }

    @Scheduled(fixedDelayString = "${order.payment-status-outbox.recovery-delay-ms:30000}")
    public void publishPaymentStatuses() {
        paymentStatusOutboxRecoveryService.publishPending();
    }
}
