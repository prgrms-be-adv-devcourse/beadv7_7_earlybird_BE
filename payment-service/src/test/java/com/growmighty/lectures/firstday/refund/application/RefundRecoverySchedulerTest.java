package com.growmighty.lectures.firstday.refund.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundRecoverySchedulerTest {

    @Mock
    private RefundRecoveryBatchService refundRecoveryBatchService;

    @InjectMocks
    private RefundRecoveryScheduler refundRecoveryScheduler;

    @Test
    void 시간_초과_환불_복구_스케줄러는_일괄_복구_서비스를_호출한다() {
        refundRecoveryScheduler.recoverTimedOutRefunds();

        verify(refundRecoveryBatchService).recoverTimedOutRefunds();
    }
}
