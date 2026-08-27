package com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 프로젝트 삭제 시 발행되는 도메인 이벤트 (project.deleted.v1).
 * file-service 등이 구독하여 연관 파일/S3 오브젝트 정리 등의 비동기 작업을 수행한다.
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
