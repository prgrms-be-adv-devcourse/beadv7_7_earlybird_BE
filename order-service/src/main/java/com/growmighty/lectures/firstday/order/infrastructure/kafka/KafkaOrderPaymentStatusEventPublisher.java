package com.growmighty.lectures.firstday.order.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.order.application.dto.OrderPaymentStatusMessage;
import com.growmighty.lectures.firstday.order.application.port.OrderPaymentStatusEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class KafkaOrderPaymentStatusEventPublisher implements OrderPaymentStatusEventPublisher {
    private static final long KAFKA_ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final OrderPaymentStatusChangedEventMapper eventMapper;

    @Override
    public void publish(OrderPaymentStatusMessage message) {
        kafkaTemplate.send(
                        KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED,
                        String.valueOf(message.orderId()),
                        eventMapper.map(message)
                )
                .orTimeout(KAFKA_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();
    }
}
