package com.growmighty.lectures.firstday.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentStatusOutboxScheduler {

    private final PaymentStatusOutboxBatchService paymentStatusOutboxBatchService;

    @Scheduled(fixedDelayString = "${payment.outbox.schedule-fixed-delay:60000}")
    public void dispatchPendingOutboxes() {
        paymentStatusOutboxBatchService .dispatchPendingOutboxes();
    }
}
