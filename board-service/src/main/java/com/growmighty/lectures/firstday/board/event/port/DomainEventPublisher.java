package com.growmighty.lectures.firstday.board.event.port;

/**
 * 도메인 코어는 Spring의 ApplicationEventPublisher를 직접 알지 못하고 이 인터페이스로만 이벤트를 발행한다.
 * 실제 발행 수단(Spring 이벤트 / 추후 Kafka)은 event.adapter 의 구현체가 담당한다.
 */
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}