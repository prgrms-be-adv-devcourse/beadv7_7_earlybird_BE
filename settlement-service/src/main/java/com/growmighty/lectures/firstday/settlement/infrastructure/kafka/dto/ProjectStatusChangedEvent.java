package com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectStatusChangedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public record Payload(Long projectId, String projectName, Long creatorId, String status) {
    }
}
