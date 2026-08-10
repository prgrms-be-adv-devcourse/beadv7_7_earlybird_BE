// TODO(settlement-plan): Keep project and creator query methods focused on confirmed settlement reads.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProjectSettlementRepository
        extends JpaRepository<ProjectSettlement, Long> {

    @Override
    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    Optional<ProjectSettlement> findById(Long id);

    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    Optional<ProjectSettlement> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    List<ProjectSettlement> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId);

    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    List<ProjectSettlement> findAllByOrderByConfirmedAtDescIdDesc();
}
