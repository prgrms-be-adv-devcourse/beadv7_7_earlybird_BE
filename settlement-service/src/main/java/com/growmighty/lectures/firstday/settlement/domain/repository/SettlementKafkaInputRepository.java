package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SettlementKafkaInputRepository {

    boolean markProcessed(UUID eventId, String eventType, Instant occurredAt);

    Optional<ProjectOutcomeFact> findProjectOutcome(Long projectId);

    void save(ProjectOutcomeFact outcome);

    Optional<OrderPaymentFact> findOrderPayment(Long orderId);

    void save(OrderPaymentFact payment);
}
