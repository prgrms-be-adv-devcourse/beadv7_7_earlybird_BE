package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.SettlementRunInputRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SettlementRunInputRepositoryAdapter implements SettlementRunInputRepository {

    private final SpringDataProjectOutcomeFactRepository outcomeRepository;
    private final SpringDataOrderPaymentFactRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectOutcomeFact> findProjectOutcomes() {
        return outcomeRepository.findAllByOrderByProjectId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPaymentFact> findCompletedPayments(Instant startInclusive, Instant endExclusive) {
        return paymentRepository.findAllByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAscOrderIdAsc(
                OrderPaymentFact.Status.COMPLETED,
                startInclusive,
                endExclusive,
                Pageable.unpaged()
        );
    }
}
