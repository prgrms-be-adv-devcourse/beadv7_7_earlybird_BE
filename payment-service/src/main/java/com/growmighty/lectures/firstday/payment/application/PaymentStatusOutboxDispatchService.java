package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentStatusChangedEvent;
import com.growmighty.lectures.firstday.payment.application.port.PaymentSingleResultEventPublisher;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentStatusOutboxDispatchService {

    private final PaymentStatusOutboxRepository  paymentStatusOutboxRepository;
    private final PaymentSingleResultEventPublisher paymentSingleResultEventPublisher;


    public void dispatch(PaymentStatusOutbox outbox) {
        if (!paymentStatusOutboxRepository.claimPending(outbox.getId())) {
            return;
        }

        try {
            paymentSingleResultEventPublisher.publish(
                new PaymentStatusChangedEvent(
                    outbox.getOrderId(),
                    outbox.getPgOrderId(),
                    outbox.getPaymentStatus().getCode()
                )
            );

            outbox.markSent();
            paymentStatusOutboxRepository.save(outbox);
        } catch (RuntimeException exception) {
            outbox.increaseRetryCount();
            paymentStatusOutboxRepository.save(outbox);
            throw exception;
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchAfterCommit(PaymentStatusOutbox outbox) {
        try {
            dispatch(outbox);
        } catch (RuntimeException exception) {
            log.warn("상태 통지 즉시 발행에 실패했습니다. outboxId ={}, retryCount={}", outbox.getId(), outbox.getRetryCount(), exception);
        }
    }
}
