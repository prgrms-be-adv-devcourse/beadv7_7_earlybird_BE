// TODO(settlement-plan): Keep aggregate mapping local and support idempotent project and month queries.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectSettlementRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectSettlementRepositoryAdapter implements ProjectSettlementRepository {

    private final SpringDataProjectSettlementRepository repository;

    @Override
    @Transactional
    public ProjectSettlement save(ProjectSettlement settlement) {
        return repository.saveAndFlush(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSettlement> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSettlement> findByProjectId(Long projectId) {
        return repository.findByProjectId(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSettlement> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId) {
        return repository.findAllByCreatorIdOrderByConfirmedAtDescIdDesc(creatorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSettlement> findAllByOrderByConfirmedAtDescIdDesc() {
        return repository.findAllByOrderByConfirmedAtDescIdDesc();
    }
}
