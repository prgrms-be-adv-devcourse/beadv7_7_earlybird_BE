package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
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

    private final ObjectMapper objectMapper;
    private final SettlementKafkaInputService inputService;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_BULK_CANCEL_RESULT,
            groupId = "settlement-service",
            containerFactory = "settlementKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment)
            throws JsonProcessingException {
        inputService.saveProjectRefundProcessed(
                record.key(),
                objectMapper.readValue(record.value(), ProjectRefundProcessedEvent.class)
        );
        acknowledgment.acknowledge();
    }
}
