package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.config.RefundRecoveryProperties;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundRecoveryBatchServiceTest {

    private static final int BATCH_SIZE = 100;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundRecoveryService refundRecoveryService;

    private RefundRecoveryBatchService refundRecoveryBatchService;

    @BeforeEach
    void setUp() {
        refundRecoveryBatchService = new RefundRecoveryBatchService(
            refundRepository,
            refundRecoveryService,
            new RefundRecoveryProperties(Duration.ofMinutes(3), BATCH_SIZE)
        );
    }

    @Test
    void 시간_초과된_REQUESTED_환불을_배치_크기만큼_복구한다() {
        when(refundRepository.findRecoveryTargetIds(any(LocalDateTime.class), eq(BATCH_SIZE)))
            .thenReturn(List.of(1L, 2L));

        refundRecoveryBatchService.recoverTimedOutRefunds();

        verify(refundRepository).findRecoveryTargetIds(any(LocalDateTime.class), eq(BATCH_SIZE));
        verify(refundRecoveryService).recover(1L);
        verify(refundRecoveryService).recover(2L);
    }

    @Test
    void 한_환불_복구에_실패해도_다음_환불을_계속_복구한다() {
        when(refundRepository.findRecoveryTargetIds(any(LocalDateTime.class), eq(BATCH_SIZE)))
            .thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("Toss 조회 실패"))
            .when(refundRecoveryService).recover(1L);

        refundRecoveryBatchService.recoverTimedOutRefunds();

        verify(refundRecoveryService).recover(1L);
        verify(refundRecoveryService).recover(2L);
    }
}
