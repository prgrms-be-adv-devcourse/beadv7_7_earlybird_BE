package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectRefundRequestedKafkaPublisher {

    private final ProjectRefundRequestedRepository outboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final Clock clock;

    public void publishPending() {
        for (ProjectRefundRequested request : outboxRepository.findPending()) {
            try {
                kafkaTemplate.send(
                        KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND,
                        request.projectId().toString(),
                        eventOf(request)
                ).join();
                request.markPublished(Instant.now(clock));
                outboxRepository.save(request);
            } catch (RuntimeException exception) {
                log.warn("프로젝트 환불 요청 Kafka 발행에 실패했습니다. eventId={}", request.eventId(), exception);
            }
        }
    }

    private ProjectRefundRequestedEvent eventOf(ProjectRefundRequested request) {
        return new ProjectRefundRequestedEvent(
                request.eventId(),
                ProjectRefundRequested.EVENT_TYPE,
                ProjectRefundRequested.SCHEMA_VERSION,
                OffsetDateTime.ofInstant(request.occurredAt(), clock.getZone()),
                new ProjectRefundRequestedEvent.Payload(
                        request.projectId(),
                        request.reason().name(),
                        request.payments().stream()
                                .map(payment -> new ProjectRefundRequestedEvent.Payment(
                                        payment.orderId(),
                                        payment.pgOrderId()
                                ))
                                .toList()
                )
        );
    }
}
