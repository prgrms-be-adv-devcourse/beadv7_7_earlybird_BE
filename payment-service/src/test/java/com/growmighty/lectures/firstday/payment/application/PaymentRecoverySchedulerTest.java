package com.growmighty.lectures.firstday.payment.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentRecoverySchedulerTest {

    @Mock
    private PaymentRecoveryBatchService paymentRecoveryBatchService;

    @InjectMocks
    private PaymentRecoveryScheduler paymentRecoveryScheduler;

    @Test
    void 타임아웃_결제_복구_스케줄러는_일괄_복구_서비스를_호출한다() {
        paymentRecoveryScheduler.recoveryTimedOutPayments();

        verify(paymentRecoveryBatchService).expireTimedOutPayments();
        verify(paymentRecoveryBatchService).recoverTimedOutPayments();
    }
}
