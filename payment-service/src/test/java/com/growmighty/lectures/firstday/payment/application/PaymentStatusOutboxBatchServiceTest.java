package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStatusOutboxBatchServiceTest {

    @Mock
    private PaymentStatusOutboxRepository paymentStatusOutboxRepository;

    @Mock
    private PaymentStatusOutboxDispatchService paymentStatusOutboxDispatchService;

    @InjectMocks
    private PaymentStatusOutboxBatchService paymentStatusOutboxBatchService;

    @Test
    void 배치_실행_전에_오래된_Processing_Outbox를_복구한다() {
        when(paymentStatusOutboxRepository.findPending(100)).thenReturn(List.of());

        paymentStatusOutboxBatchService.dispatchPendingOutboxes();

        verify(paymentStatusOutboxRepository).recoverStaleProcessing(any(LocalDateTime.class));
    }
}
