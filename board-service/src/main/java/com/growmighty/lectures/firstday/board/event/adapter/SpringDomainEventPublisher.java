package com.growmighty.lectures.firstday.board.event.adapter;

import com.growmighty.lectures.firstday.board.event.port.DomainEvent;
import com.growmighty.lectures.firstday.board.event.port.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * DomainEventPublisher의 MVP 구현체 — Spring의 ApplicationEventPublisher를 감싼다.
 * 나중에 Kafka로 전환할 때는 이 클래스를 KafkaDomainEventPublisher로 교체(또는 추가)하기만 하면 되고,
 * 도메인 코어(ReviewService 등)와 리스너는 변경할 필요가 없다.
 */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}