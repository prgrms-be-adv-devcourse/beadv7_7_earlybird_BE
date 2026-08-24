package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInput;
import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInputService;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.OrderPaymentStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaymentStatusChangedKafkaListener {

    private final SettlementKafkaInputService inputService;

    @KafkaListener(
            topics = KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED,
            groupId = "settlement-service",
            containerFactory = "settlementKafkaListenerContainerFactory",
            properties = "spring.json.type.mapping=orderPaymentStatusChanged:com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.OrderPaymentStatusChangedEvent"
    )
    public void consume(ConsumerRecord<String, OrderPaymentStatusChangedEvent> record, Acknowledgment acknowledgment) {
        OrderPaymentStatusChangedEvent event = record.value();
        inputService.saveOrderPaymentStatus(new SettlementKafkaInput.OrderPaymentStatusChanged(
                record.key(),
                event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(),
                event.payload().orderId(), event.payload().pgOrderId(), event.payload().projectId(),
                event.payload().paymentAmount(), event.payload().status()
        ));
        acknowledgment.acknowledge();
    }
}
