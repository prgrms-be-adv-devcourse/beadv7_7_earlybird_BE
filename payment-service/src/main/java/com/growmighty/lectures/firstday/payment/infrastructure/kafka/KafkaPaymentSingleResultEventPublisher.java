package com.growmighty.lectures.firstday.payment.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.payment.application.port.PaymentSingleResultEventPublisher;
import com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto.PaymentSingleResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaPaymentSingleResultEventPublisher implements PaymentSingleResultEventPublisher {

    private final KafkaTemplate<String, PaymentSingleResultEvent> kafkaTemplate;

    @Override
    public void publish(PaymentSingleResultEvent event) {
        kafkaTemplate.send(
            KafkaTopics.PAYMENT_SINGLE_RESULT,
            String.valueOf(event.orderId()),
            event
        ).join();
    }
}
