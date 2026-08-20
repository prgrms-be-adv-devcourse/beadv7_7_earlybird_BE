package com.growmighty.lectures.firstday.refund.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.refund.domain.*;
import com.growmighty.lectures.firstday.refund.infrastructure.dto.ProjectRefundProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkRefundResultOutboxPublisher {

    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 5L;
    private static final String EVENT_TYPE = "ProjectRefundProcessed";
    private static final int SCHEMA_VERSION = 1;

    private final BulkRefundResultOutboxRepository bulkRefundResultOutboxRepository;
    private final RefundRepository  refundRepository;
    private final KafkaTemplate<String, ProjectRefundProcessedEvent> kafkaTemplate;

    @Scheduled(fixedDelayString = "${payment.outbox.schedule-fixed-delay:60000}")
    public void publishPending() {
        // TODO: 다중 인스턴스 확장 시 결과 Outbox 발행 작업을 원자적으로 선점해야 한다. // <--
        List<BulkRefundResultOutbox> outboxes = bulkRefundResultOutboxRepository.findPending(BATCH_SIZE);

        if (outboxes.isEmpty()) {
            return;
        }

        Map<Long, List<BulkRefundOrder>> ordersByRefundRequestId =
            refundRepository.findOrdersByRefundRequestIds(
                outboxes.stream()
                    .map(BulkRefundResultOutbox::getRefundRequestId)
                    .toList()
            ).stream().collect(Collectors.groupingBy(BulkRefundOrder::refundRequestId));

        for (BulkRefundResultOutbox outbox : outboxes) {
            List<Long> orderIds = ordersByRefundRequestId.getOrDefault(outbox.getRefundRequestId(), List.of())
                .stream()
                .filter(order -> hasSameResultStatus(
                    order.refundStatus(),
                    outbox.getResultStatus()
                ))
                .map(BulkRefundOrder::orderId)
                .toList();

            publish(outbox, orderIds);
        }
    }

    private boolean hasSameResultStatus(RefundStatus refundStatus, BulkRefundResultStatus resultStatus) {
        return switch (resultStatus) {
            case COMPLETED -> refundStatus == RefundStatus.COMPLETED;
            case FAILED -> refundStatus == RefundStatus.FAILED;
        };
    }

    private void publish(BulkRefundResultOutbox outbox, List<Long> orderIds) {
        try {
            kafkaTemplate.send(
                KafkaTopics.PAYMENT_BULK_CANCEL_RESULT,
                String.valueOf(outbox.getRefundRequestId()),
                eventOf(outbox, orderIds)
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            outbox.markSent();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            outbox.increaseRetryCount();
            log.warn("일괄 취소 결과 kafka 발행이 인터럽트 되었습니다. refundRequestId = {}", outbox.getRefundRequestId(), exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            outbox.increaseRetryCount();
            log.warn("일괄 취소 결과 kafka 발행에 실패했습니다. refundRequestId = {}", outbox.getRefundRequestId(), exception);
        }

        bulkRefundResultOutboxRepository.save(outbox);
    }

    private ProjectRefundProcessedEvent eventOf(BulkRefundResultOutbox outbox, List<Long> orderIds) {
        return new ProjectRefundProcessedEvent(
            UUID.nameUUIDFromBytes(
                ("ProjectRefundProcessed: "
                    + outbox.getRefundRequestId()
                    + ":"
                    + outbox.getResultStatus().getCode())
                    .getBytes(StandardCharsets.UTF_8)
            ),
            EVENT_TYPE,
            SCHEMA_VERSION,
            OffsetDateTime.now(),
            new ProjectRefundProcessedEvent.Payload(
                outbox.getRefundRequestId(),
                orderIds,
                outbox.getResultStatus().getCode()
            )
        );
    }
}
