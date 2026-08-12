package com.growmighty.lectures.firstday.payment.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.payment.application.port.PaymentSingleResultEventPublisher;
import com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto.PaymentSingleResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class KafkaPaymentSingleResultEventPublisher implements PaymentSingleResultEventPublisher {

    private final KafkaTemplate<String, PaymentSingleResultEvent> kafkaTemplate;

    private static final long SEND_TIMEOUT_SECONDS = 5L;

    @Override
    public void publish(PaymentSingleResultEvent event) {
        try {
            kafkaTemplate.send(
                KafkaTopics.PAYMENT_SINGLE_RESULT,
                String.valueOf(event.orderId()),
                event
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kakfa 메시지 발행이 인터럽트 되었습니다. ", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Kafka 메시지 발행에 실패했습니다. ", exception);
        }
    }
}
