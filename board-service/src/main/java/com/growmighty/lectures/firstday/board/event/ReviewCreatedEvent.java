package com.growmighty.lectures.firstday.board.event;

import com.growmighty.lectures.firstday.board.event.port.DomainEvent;

import java.time.LocalDateTime;

/**
 * 리뷰가 생성됐다는 이벤트. Review 엔티티를 직접 담지 않고, 리스너가 필요로 하는 원시/래퍼/String 필드만 추출해서 담는다
 * (LAZY 프록시 직렬화 문제 방지 + 향후 Kafka 메시지로 그대로 실어보낼 수 있는 형태 유지).
 */
public record ReviewCreatedEvent(
    Long reviewId,
    Long projectId,
    Long authorId,
    String authorName,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static ReviewCreatedEvent of(Long reviewId, Long projectId, Long authorId, String authorName) {
        return new ReviewCreatedEvent(reviewId, projectId, authorId, authorName, LocalDateTime.now());
    }
}