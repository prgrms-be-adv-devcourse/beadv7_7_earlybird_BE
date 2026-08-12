package com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProjectRefundRequestedEvent(
        String settlementId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public record Payload(String settlementId, List<Long> orderIds) {
    }
}
