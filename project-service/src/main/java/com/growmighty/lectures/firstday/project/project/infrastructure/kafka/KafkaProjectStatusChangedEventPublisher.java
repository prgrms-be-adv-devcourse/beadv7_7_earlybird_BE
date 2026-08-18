package com.growmighty.lectures.firstday.project.project.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectStatusChangedEventPublisher;
import com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto.ProjectStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class KafkaProjectStatusChangedEventPublisher implements ProjectStatusChangedEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, ProjectStatusChangedEvent> kafkaTemplate;

    @Override
    public void publish(ProjectStatusChangedEvent event) {
        try {
            kafkaTemplate.send(
                    KafkaTopics.PROJECT_STATUS_CHANGED,
                    String.valueOf(event.payload().projectId()),
                    event
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 메시지 발행이 인터럽트 되었습니다.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Kafka 메시지 발행에 실패했습니다.", exception);
        }
    }
}
