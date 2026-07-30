package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.port.OrderStatusPort;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentStatusOutboxDispatchService {

    private final PaymentStatusOutboxRepository  paymentStatusOutboxRepository;
    private final OrderStatusPort orderStatusPort;


    public void dispatch(PaymentStatusOutbox outbox) {
        try {
            orderStatusPort.notifyStatus(
                outbox.getOrderId(),
                outbox.getPaymentStatus()
            );

            outbox.markSent();
            paymentStatusOutboxRepository.save(outbox);
        } catch (RuntimeException e) {
            outbox.increaseRetryCount();
            paymentStatusOutboxRepository.save(outbox);
            throw e;
        }
    }
}
