// TODO(settlement-plan): Keep aggregate mapping local and support idempotent project and month queries.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.ProjectSettlementJpaEntity;
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
        if (settlement.id() != null) {
            throw new IllegalStateException("확정된 프로젝트 정산은 다시 저장할 수 없습니다.");
        }
        ProjectSettlementJpaEntity saved = repository.saveAndFlush(
                ProjectSettlementJpaEntity.fromDomain(settlement)
        );
        return saved.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSettlement> findById(Long id) {
        return repository.findById(id).map(ProjectSettlementJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSettlement> findByProjectId(Long projectId) {
        return repository.findByProjectId(projectId).map(ProjectSettlementJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSettlement> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId) {
        return repository.findAllByCreatorIdOrderByConfirmedAtDescIdDesc(creatorId).stream()
                .map(ProjectSettlementJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSettlement> findAllByOrderByConfirmedAtDescIdDesc() {
        return repository.findAllByOrderByConfirmedAtDescIdDesc().stream()
                .map(ProjectSettlementJpaEntity::toDomain)
                .toList();
    }
}
