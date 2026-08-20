package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.SettlementKafkaInputRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.inbox.KafkaInboxEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataKafkaInboxEventRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementKafkaInputRepositoryAdapter implements SettlementKafkaInputRepository, ProjectOutcomeFactRepository {

    private final SpringDataKafkaInboxEventRepository inboxRepository;
    private final SpringDataProjectOutcomeFactRepository outcomeRepository;
    private final SpringDataOrderPaymentFactRepository paymentRepository;

    @Override
    public boolean markProcessed(UUID eventId, String eventType, Instant occurredAt) {
        if (inboxRepository.existsById(eventId.toString())) {
            return false;
        }
        inboxRepository.save(KafkaInboxEvent.processed(eventId, eventType, occurredAt));
        return true;
    }

    @Override
    public Optional<ProjectOutcomeFact> findProjectOutcome(Long projectId) {
        return outcomeRepository.findById(projectId);
    }

    @Override
    public List<ProjectOutcomeFact> findAllByProjectIdIn(Collection<Long> projectIds) {
        return outcomeRepository.findAllById(projectIds);
    }

    @Override
    public void save(ProjectOutcomeFact outcome) {
        outcomeRepository.save(outcome);
    }

    @Override
    public Optional<OrderPaymentFact> findOrderPayment(Long orderId) {
        return paymentRepository.findById(orderId);
    }

    @Override
    public void save(OrderPaymentFact payment) {
        paymentRepository.save(payment);
    }
}
