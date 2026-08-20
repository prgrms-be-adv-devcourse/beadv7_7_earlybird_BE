package com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProjectRefundRequestedEvent(
        String refundRequestId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public record Payload(String refundRequestId, List<Long> orderIds) {
    }
}
