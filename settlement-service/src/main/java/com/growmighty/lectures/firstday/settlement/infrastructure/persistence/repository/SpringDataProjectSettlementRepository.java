// TODO(settlement-plan): Keep project and creator query methods focused on confirmed settlement reads.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProjectSettlementRepository
        extends JpaRepository<ProjectSettlement, Long> {

    Optional<ProjectSettlement> findByProjectId(Long projectId);

    List<ProjectSettlement> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId);

    List<ProjectSettlement> findAllByOrderByConfirmedAtDescIdDesc();
}
