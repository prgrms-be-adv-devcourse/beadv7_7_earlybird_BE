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

    @Scheduled(fixedDelayString = "${order.saga.recovery-delay-ms:30000}")
    public void recover() {
        orderSagaRecoveryService.recoverPendingOrders();
    }
}
