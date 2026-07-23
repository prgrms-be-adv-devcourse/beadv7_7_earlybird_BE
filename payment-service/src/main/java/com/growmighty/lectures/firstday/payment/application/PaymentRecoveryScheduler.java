package com.growmighty.lectures.firstday.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentRecoveryBatchService paymentRecoveryBatchService;


    /**
     * Config 서버에 필요하면 복구 주기를 변경할 수 있다.
     * 예시 :
     payment:
     recovery:
     schedule-fixed-delay: 180000
     */
    @Scheduled(fixedDelayString = "${payment.recovery.schedule-fixed-delay:180000}")
    public void recoveryTimedOutPayments() {
        paymentRecoveryBatchService.recoverTimedOutPayments();
    }
}
