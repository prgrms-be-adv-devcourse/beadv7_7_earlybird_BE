package com.growmighty.lectures.firstday.order.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderPaymentStatusChangedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public record Payload(
            Long orderId,
            String pgOrderId,
            Long projectId,
            Long paymentAmount,
            String status
    ) {
    }
}
