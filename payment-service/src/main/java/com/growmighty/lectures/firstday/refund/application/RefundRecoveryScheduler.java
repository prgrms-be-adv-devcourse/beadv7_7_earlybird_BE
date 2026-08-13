package com.growmighty.lectures.firstday.refund.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundRecoveryScheduler {
    private final RefundRecoveryBatchService refundRecoveryBatchService;

    @Scheduled(fixedDelayString = "${payment.refund-recovery.schedule-fixed-delay:180000}")
    public void recoverTimedOutRefunds() {
        refundRecoveryBatchService.recoverTimedOutRefunds();
    }
}
