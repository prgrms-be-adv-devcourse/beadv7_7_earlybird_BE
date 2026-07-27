package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
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
class PaymentRecoveryBatchServiceTest {

    private static final int BATCH_SIZE = 100;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentRecoveryService paymentRecoveryService;

    private PaymentRecoveryBatchService paymentRecoveryBatchService;

    @BeforeEach
    void setUp() {
        PaymentRecoveryProperties properties = new PaymentRecoveryProperties(
            Duration.ofMinutes(3),
            BATCH_SIZE
        );

        paymentRecoveryBatchService = new PaymentRecoveryBatchService(
            paymentRepository,
            paymentRecoveryService,
            properties
        );
    }

    @Test
    void 타임아웃된_CONFIRMING_결제를_배치_크기만큼_조회해_복구한다() {
        when(paymentRepository.findConfirmingPaymentIdsBefore(any(LocalDateTime.class), eq(BATCH_SIZE)))
            .thenReturn(List.of(1L, 2L));

        paymentRecoveryBatchService.recoverTimedOutPayments();

        verify(paymentRepository).findConfirmingPaymentIdsBefore(any(LocalDateTime.class), eq(BATCH_SIZE));
        verify(paymentRecoveryService).recover(1L);
        verify(paymentRecoveryService).recover(2L);
    }

    @Test
    void 한_결제의_복구에_실패해도_다음_결제를_계속_복구한다() {
        when(paymentRepository.findConfirmingPaymentIdsBefore(any(LocalDateTime.class), eq(BATCH_SIZE)))
            .thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("Toss 조회 실패"))
            .when(paymentRecoveryService).recover(1L);

        paymentRecoveryBatchService.recoverTimedOutPayments();

        verify(paymentRecoveryService).recover(1L);
        verify(paymentRecoveryService).recover(2L);
    }
}
