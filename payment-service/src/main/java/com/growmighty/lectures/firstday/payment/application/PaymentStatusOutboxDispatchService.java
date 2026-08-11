package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.port.PaymentSingleResultEventPublisher;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto.PaymentSingleResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentStatusOutboxDispatchService {

    private final PaymentStatusOutboxRepository  paymentStatusOutboxRepository;
    private final PaymentSingleResultEventPublisher paymentSingleResultEventPublisher;


    public void dispatch(PaymentStatusOutbox outbox) {
        try {
            paymentSingleResultEventPublisher.publish(
                new PaymentSingleResultEvent(
                    outbox.getOrderId(),
                    outbox.getPgOrderId(),
                    outbox.getPaymentStatus().name()
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
}
