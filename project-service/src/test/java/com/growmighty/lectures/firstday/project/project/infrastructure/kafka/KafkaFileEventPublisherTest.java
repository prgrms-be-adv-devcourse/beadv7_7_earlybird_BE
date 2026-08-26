package com.growmighty.lectures.firstday.project.project.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto.ProjectDeletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaFileEventPublisherTest {

    private static final String TOPIC_PROJECT_DELETED = KafkaTopics.PROJECT_DELETED;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ProjectDeletedEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaFileEventPublisher publisher = new KafkaFileEventPublisher(kafkaTemplate);

    @Test
    @DisplayName("deleteProjectFiles 호출 시 Kafka로 ProjectDeletedEvent를 발행한다")
    void deleteProjectFiles_publishesEvent() {
        when(kafkaTemplate.send(eq(TOPIC_PROJECT_DELETED), eq("1"), any(ProjectDeletedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.deleteProjectFiles(1L);

        verify(kafkaTemplate).send(eq(TOPIC_PROJECT_DELETED), eq("1"), any(ProjectDeletedEvent.class));
    }

    @Test
    @DisplayName("Kafka 발행이 실패해도 예외를 던지지 않고 무시한다 (best-effort)")
    void deleteProjectFiles_whenKafkaFails_swallowsException() {
        CompletableFuture<SendResult<String, ProjectDeletedEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker down"));

        when(kafkaTemplate.send(eq(TOPIC_PROJECT_DELETED), eq("1"), any(ProjectDeletedEvent.class)))
                .thenReturn(failedFuture);

        // 예외가 전파되지 않아야 함
        publisher.deleteProjectFiles(1L);

        verify(kafkaTemplate).send(eq(TOPIC_PROJECT_DELETED), eq("1"), any(ProjectDeletedEvent.class));
    }
}
