package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.inbox.KafkaInboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataKafkaInboxEventRepository extends JpaRepository<KafkaInboxEvent, String> {
}
