package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.ProjectSettlementJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProjectSettlementRepository
        extends JpaRepository<ProjectSettlementJpaEntity, Long> {

    Optional<ProjectSettlementJpaEntity> findByProjectId(Long projectId);

    List<ProjectSettlementJpaEntity> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId);
}
