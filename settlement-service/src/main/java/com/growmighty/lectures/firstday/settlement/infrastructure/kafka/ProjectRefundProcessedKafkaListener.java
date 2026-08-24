package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInput;
import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInputService;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundProcessedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectRefundProcessedKafkaListener {

    private final SettlementKafkaInputService inputService;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_BULK_CANCEL_RESULT,
            groupId = "settlement-service",
            containerFactory = "settlementKafkaListenerContainerFactory",
            properties = "spring.json.type.mapping=projectRefundProcessed:com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundProcessedEvent"
    )
    public void consume(ConsumerRecord<String, ProjectRefundProcessedEvent> record, Acknowledgment acknowledgment) {
        ProjectRefundProcessedEvent event = record.value();
        inputService.saveProjectRefundProcessed(new SettlementKafkaInput.ProjectRefundProcessed(
                record.key(),
                event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(),
                event.payload().refundRequestId(), event.payload().orderIds(), event.payload().status()
        ));
        acknowledgment.acknowledge();
    }
}
