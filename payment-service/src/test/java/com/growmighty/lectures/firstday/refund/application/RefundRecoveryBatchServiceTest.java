package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.domain.vo.SensitiveValue;
import com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget;
import com.growmighty.lectures.firstday.refund.application.port.RefundRecoveryTargetReader;
import com.growmighty.lectures.firstday.refund.config.RefundRecoveryProperties;
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
    private RefundRecoveryTargetReader refundRecoveryTargetReader;

    @Mock
    private RefundRecoveryService refundRecoveryService;

    private RefundRecoveryBatchService refundRecoveryBatchService;

    @BeforeEach
    void setUp() {
        refundRecoveryBatchService = new RefundRecoveryBatchService(
            refundRecoveryService,
            new RefundRecoveryProperties(
                Duration.ofMinutes(3),
                BATCH_SIZE,
                3,
                Duration.ofMinutes(5)
            ),
            refundRecoveryTargetReader // <-- 복구 대상 일괄 조회 포트
        );
    }

    @Test
    void 시간_초과된_REQUESTED_환불을_배치_크기만큼_복구한다() {
        when(refundRecoveryTargetReader.findTimedOutRequestTargets(any(LocalDateTime.class), eq(BATCH_SIZE)))
            .thenReturn(List.of(target(1L), target(2L))); // <-- Refund와 paymentKey를 함께 조회

        refundRecoveryBatchService.recoverTimedOutRefunds();

        verify(refundRecoveryTargetReader).findTimedOutRequestTargets(any(LocalDateTime.class), eq(BATCH_SIZE));
        verify(refundRecoveryService).recover(target(1L)); // <-- DTO로 복구 위임
        verify(refundRecoveryService).recover(target(2L)); // <-- DTO로 복구 위임
    }

    @Test
    void 한_환불_복구에_실패해도_다음_환불을_계속_복구한다() {
        when(refundRecoveryTargetReader.findTimedOutRequestTargets(any(LocalDateTime.class), eq(BATCH_SIZE)))
            .thenReturn(List.of(target(1L), target(2L))); // <-- Refund와 paymentKey를 함께 조회
        doThrow(new IllegalStateException("Toss 조회 실패"))
            .when(refundRecoveryService).recover(target(1L)); // <-- 첫 대상만 실패

        refundRecoveryBatchService.recoverTimedOutRefunds();

        verify(refundRecoveryService).recover(target(1L)); // <-- 실패 후에도 다음 대상 진행
        verify(refundRecoveryService).recover(target(2L)); // <-- 실패 후에도 다음 대상 진행
    }

    // 추가 : 복구 대상 DTO 생성, 배치 테스트 공통 입력값
    private RefundRecoveryTarget target(Long refundId) {
        return new RefundRecoveryTarget(refundId, new SensitiveValue("payment-key-" + refundId));
    }
}
