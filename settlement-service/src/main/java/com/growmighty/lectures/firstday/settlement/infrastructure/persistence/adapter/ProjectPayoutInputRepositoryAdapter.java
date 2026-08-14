package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPayoutInputRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectPayoutInputRepositoryAdapter implements ProjectPayoutInputRepository {

    private final SpringDataProjectOutcomeFactRepository outcomeRepository;
    private final SpringDataOrderPaymentFactRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectOutcomeFact> findSucceededProjects() {
        return outcomeRepository.findAllByOutcomeOrderByProjectId(ProjectOutcomeFact.Outcome.SUCCEEDED);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPaymentFact> findCompletedPaymentsByProjectId(Long projectId) {
        return paymentRepository.findAllByProjectIdAndStatusOrderByOrderId(
                projectId,
                OrderPaymentFact.Status.COMPLETED
        );
    }
}
