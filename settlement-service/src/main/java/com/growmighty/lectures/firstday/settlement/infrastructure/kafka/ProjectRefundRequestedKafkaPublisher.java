package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectRefundRequestedKafkaPublisher {

    private static final long KAFKA_ACK_TIMEOUT_SECONDS = 10;

    private final ProjectRefundRequestedRepository outboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final Clock clock;

    public void publishPending() {
        for (ProjectRefundRequested request : outboxRepository.findPending()) {
            try {
                kafkaTemplate.send(
                        KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND,
                        request.refundRequestId(),
                        eventOf(request)
                ).orTimeout(KAFKA_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
                request.markPublished(Instant.now(clock));
                outboxRepository.save(request);
            } catch (RuntimeException exception) {
                log.warn("프로젝트 환불 요청 Kafka 발행에 실패했습니다. refundRequestId={}", request.refundRequestId(), exception);
            }
        }
    }

    private ProjectRefundRequestedEvent eventOf(ProjectRefundRequested request) {
        // 명세 변경 비용 때문에 외부 settlementId에 내부 refundRequestId를 매핑한다. 계약 변경 시 제거할 수 있다.
        return new ProjectRefundRequestedEvent(
                request.refundRequestId(),
                ProjectRefundRequested.EVENT_TYPE,
                ProjectRefundRequested.SCHEMA_VERSION,
                OffsetDateTime.ofInstant(request.occurredAt(), clock.getZone()),
                new ProjectRefundRequestedEvent.Payload(
                        request.refundRequestId(),
                        request.payments().stream()
                                .map(ProjectRefundRequested.Payment::orderId)
                                .toList()
                )
        );
    }
}
