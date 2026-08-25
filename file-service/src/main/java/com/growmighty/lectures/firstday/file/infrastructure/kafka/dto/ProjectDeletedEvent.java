package com.growmighty.lectures.firstday.file.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * project-service가 프로젝트 삭제 시 발행하는 이벤트 (project.deleted.v1).
 */
public record ProjectDeletedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public record Payload(Long projectId) {
    }

    public static ProjectDeletedEvent of(Long projectId) {
        return new ProjectDeletedEvent(
                UUID.randomUUID(),
                "ProjectDeleted",
                1,
                OffsetDateTime.now(),
                new Payload(projectId)
        );
    }
}
