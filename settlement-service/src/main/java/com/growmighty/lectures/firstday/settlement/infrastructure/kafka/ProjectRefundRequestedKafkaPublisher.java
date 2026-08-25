package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.PaymentBulkCancelCommand;
import java.time.Clock;
import java.time.Instant;
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
                        String.valueOf(request.refundRequestId()),
                        eventOf(request)
                ).orTimeout(KAFKA_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
                request.markPublished(Instant.now(clock));
                outboxRepository.save(request);
            } catch (RuntimeException exception) {
                log.warn("프로젝트 환불 요청 Kafka 발행에 실패했습니다. refundRequestId={}", request.refundRequestId(), exception);
            }
        }
    }

    private PaymentBulkCancelCommand eventOf(ProjectRefundRequested request) {
        return new PaymentBulkCancelCommand(
                request.refundRequestId(),
                request.payments().stream()
                        .map(ProjectRefundRequested.Payment::orderId)
                        .toList(),
                reasonOf(request)
        );
    }

    private static String reasonOf(ProjectRefundRequested request) {
        return switch (request.reason()) {
            case PROJECT_FAILED -> "GOAL_FAILED";
            case PROJECT_CANCELLED -> "USER_CANCEL";
        };
    }
}
