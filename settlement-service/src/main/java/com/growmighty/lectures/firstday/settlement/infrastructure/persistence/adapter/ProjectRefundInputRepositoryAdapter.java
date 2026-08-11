package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundInputRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectRefundInputRepositoryAdapter implements ProjectRefundInputRepository {

    private final SpringDataProjectOutcomeFactRepository outcomeRepository;
    private final SpringDataOrderPaymentFactRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectOutcomeFact> findRefundOutcomes(Instant dueBefore, int limit) {
        return outcomeRepository.findRefundOutcomesDueBefore(
                List.of(
                        ProjectOutcomeFact.Outcome.FAILED,
                        ProjectOutcomeFact.Outcome.CANCELLED
                ),
                dueBefore,
                PageRequest.of(0, limit)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPaymentFact> findPayments(Long projectId) {
        return paymentRepository.findAllByProjectIdOrderByOrderId(projectId);
    }
}
