package com.growmighty.lectures.firstday.payment.application;


import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentStatusOutboxAppender {
    private final PaymentStatusOutboxRepository paymentStatusOutboxRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void appendIfAbsent(Payment payment) {
        if (paymentStatusOutboxRepository.existsByPaymentIdAndPaymentStatus(payment.getPaymentId(), payment.getStatus())) {
            return;
        }

        PaymentStatusOutbox outbox = paymentStatusOutboxRepository.save(
            PaymentStatusOutbox.pending(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getPgOrderId(),
                payment.getStatus()
            )
        );

        applicationEventPublisher.publishEvent(outbox);
    }
}
