package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInput;
import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInputService;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectStatusChangedKafkaListener {

    private final SettlementKafkaInputService inputService;

    @KafkaListener(
            topics = KafkaTopics.PROJECT_STATUS_CHANGED,
            groupId = "settlement-service",
            containerFactory = "settlementKafkaListenerContainerFactory",
            properties = "spring.json.type.mapping=projectStatusChanged:com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectStatusChangedEvent"
    )
    public void consume(ConsumerRecord<String, ProjectStatusChangedEvent> record, Acknowledgment acknowledgment) {
        ProjectStatusChangedEvent event = record.value();
        inputService.saveProjectStatus(new SettlementKafkaInput.ProjectStatusChanged(
                record.key(),
                event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(),
                event.payload().projectId(), event.payload().projectName(), event.payload().creatorId(), event.payload().status()
        ));
        acknowledgment.acknowledge();
    }
}
