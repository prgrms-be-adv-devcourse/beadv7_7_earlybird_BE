package com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * settlement-service의 ProjectStatusChangedEvent(project.status-changed.v1 컨슈머)와 필드가 동일한
 * 계약 — settlement이 이미 이 형태(eventId/eventType/schemaVersion/occurredAt/payload)로 소비 중이라
 * 이쪽에서 임의로 바꾸면 안 된다.
 *
 * <p>#417: payload에 projectName 추가 (관리자/창작자 정산 내역 조회 시 식별용). Jackson 기본 설정상
 * 알 수 없는 필드는 무시되므로 settlement 쪽이 아직 이 필드를 안 읽어도 기존 소비는 깨지지 않지만,
 * settlement도 필요 시 자기 쪽 사본에 같은 필드를 추가해야 값을 쓸 수 있다.
 */
public record ProjectStatusChangedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        OffsetDateTime occurredAt,
        Payload payload
) {
    public record Payload(Long projectId, String projectName, Long creatorId, String status) {
    }

    public static ProjectStatusChangedEvent of(Long projectId, String projectName, Long creatorId, String status) {
        return new ProjectStatusChangedEvent(
                UUID.randomUUID(), "ProjectStatusChanged", 1, OffsetDateTime.now(),
                new Payload(projectId, projectName, creatorId, status));
    }
}
