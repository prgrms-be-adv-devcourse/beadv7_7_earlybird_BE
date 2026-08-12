package com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProjectRefundRequestedEvent(
        String eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public record Payload(Long projectId, String reason, List<Payment> payments) {
    }

    public record Payment(Long orderId, String pgOrderId) {
    }
}
