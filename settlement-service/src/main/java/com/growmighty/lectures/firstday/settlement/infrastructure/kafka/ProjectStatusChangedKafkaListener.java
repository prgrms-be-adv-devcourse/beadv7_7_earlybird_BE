package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper;
    private final SettlementKafkaInputService inputService;

    @KafkaListener(
            topics = KafkaTopics.PROJECT_STATUS_CHANGED,
            groupId = "settlement-service",
            containerFactory = "settlementKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment)
            throws JsonProcessingException {
        ProjectStatusChangedEvent event = objectMapper.readValue(record.value(), ProjectStatusChangedEvent.class);
        inputService.saveProjectStatus(new SettlementKafkaInput.ProjectStatusChanged(
                record.key(),
                event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(),
                event.payload().projectId(), event.payload().projectName(), event.payload().creatorId(), event.payload().status()
        ));
        acknowledgment.acknowledge();
    }
}
