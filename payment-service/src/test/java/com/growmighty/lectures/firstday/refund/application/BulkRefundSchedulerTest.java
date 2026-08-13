package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkRefundSchedulerTest {

    private static final Long REFUND_ID = 1L;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundCancellationSagaOrchestrator refundCancellationSagaOrchestrator;

    @InjectMocks
    private BulkRefundScheduler bulkRefundScheduler;

    @Test
    void 계획된_환불_한건을_조회해_취소_Saga에_위임한다() {
        when(refundRepository.findNextPlannedRefundId()).thenReturn(Optional.of(REFUND_ID));

        bulkRefundScheduler.cancelNextPlannedRefund();

        verify(refundCancellationSagaOrchestrator).cancelPlannedRefund(REFUND_ID);
    }
}
