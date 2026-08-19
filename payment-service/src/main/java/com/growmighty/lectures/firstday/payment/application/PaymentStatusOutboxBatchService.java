package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStatusOutboxBatchService {

    private static final int BATCH_SIZE = 100;

    private final PaymentStatusOutboxRepository paymentStatusOutboxRepository;
    private final PaymentStatusOutboxDispatchService  paymentStatusOutboxDispatchService;

    public void dispatchPendingOutboxes() {
        paymentStatusOutboxRepository.recoverStaleProcessing(
            LocalDateTime.now().minusMinutes(5)
        );

        List<PaymentStatusOutbox> outboxes = paymentStatusOutboxRepository.findPending(BATCH_SIZE);

        for (PaymentStatusOutbox outbox : outboxes) {
            try {
                paymentStatusOutboxDispatchService.dispatch(outbox);
            } catch (RuntimeException e) {
                log.warn(
                    "Order 상태 통지 재시도에 실패했습니다. outboxId={}",
                    outbox.getId(),
                    e
                );
            }
        }
    }
}
