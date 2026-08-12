package com.growmighty.lectures.firstday.settlement.infrastructure.kafka.inbox;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "settlement_kafka_inbox_events")
public class KafkaInboxEvent extends BaseEntity {

    public enum ProcessingStatus {
        PROCESSED
    }

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, updatable = false, length = 20)
    private ProcessingStatus processingStatus;

    protected KafkaInboxEvent() {
    }

    private KafkaInboxEvent(UUID eventId, String eventType, Instant occurredAt) {
        this.eventId = eventId.toString();
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.processingStatus = ProcessingStatus.PROCESSED;
        validateState();
    }

    public static KafkaInboxEvent processed(UUID eventId, String eventType, Instant occurredAt) {
        return new KafkaInboxEvent(eventId, eventType, occurredAt);
    }

    public String eventId() {
        return eventId;
    }

    public String eventType() {
        return eventType;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public ProcessingStatus processingStatus() {
        return processingStatus;
    }

    @PostLoad
    private void validateState() {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Inbox 이벤트 식별자는 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Inbox 이벤트 타입은 필수입니다.");
        }
        Objects.requireNonNull(occurredAt, "Inbox 이벤트 발생 시각은 필수입니다.");
        Objects.requireNonNull(processingStatus, "Inbox 처리 상태는 필수입니다.");
    }
}
