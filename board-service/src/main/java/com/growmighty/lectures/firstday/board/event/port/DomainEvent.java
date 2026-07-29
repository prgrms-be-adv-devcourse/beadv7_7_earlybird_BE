package com.growmighty.lectures.firstday.board.event.port;

import java.time.LocalDateTime;

/**
 * 도메인 이벤트 공통 계약. occurredAt()을 강제해서, 향후 Kafka 어댑터가 이벤트 종류별로
 * instanceof 분기 없이도 발행 시각 등 공통 메타데이터를 다룰 수 있게 한다.
 */
public interface DomainEvent {
    LocalDateTime occurredAt();
}