package com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * settlement-service의 ProjectStatusChangedEvent(project.status-changed.v1 컨슈머)와 필드가 동일한
 * 계약 — settlement이 이미 이 형태(eventId/eventType/schemaVersion/occurredAt/payload)로 소비 중이라
 * 이쪽에서 임의로 바꾸면 안 된다.
 */
public record ProjectStatusChangedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public record Payload(Long projectId, Long creatorId, String status) {
    }

    public static ProjectStatusChangedEvent of(Long projectId, Long creatorId, String status) {
        return new ProjectStatusChangedEvent(
                UUID.randomUUID(), "ProjectStatusChanged", 1, OffsetDateTime.now(),
                new Payload(projectId, creatorId, status));
    }
}
