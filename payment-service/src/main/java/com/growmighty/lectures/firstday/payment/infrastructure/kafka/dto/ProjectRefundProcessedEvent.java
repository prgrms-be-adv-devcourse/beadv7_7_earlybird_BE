package com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProjectRefundProcessedEvent(
    UUID eventId,
    String eventType,
    int schemaVersion,
    OffsetDateTime occurredAt,
    Payload payload
) {
    public record Payload(
        String settlementId,
        List<Long> orderIds,
        String status
    ) {

    }
}
