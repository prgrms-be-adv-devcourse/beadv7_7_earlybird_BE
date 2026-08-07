// TODO(settlement-plan): Support idempotent project confirmation and query reuse without exposing JPA entities.
package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import java.util.List;
import java.util.Optional;

public interface ProjectSettlementRepository {

    ProjectSettlement save(ProjectSettlement settlement);

    Optional<ProjectSettlement> findById(Long id);

    Optional<ProjectSettlement> findByProjectId(Long projectId);

    List<ProjectSettlement> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId);

    List<ProjectSettlement> findAllByOrderByConfirmedAtDescIdDesc();
}
