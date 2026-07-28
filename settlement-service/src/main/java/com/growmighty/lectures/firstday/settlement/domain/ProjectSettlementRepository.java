package com.growmighty.lectures.firstday.settlement.domain;

import java.util.List;
import java.util.Optional;

public interface ProjectSettlementRepository {

    ProjectSettlement save(ProjectSettlement settlement);

    Optional<ProjectSettlement> findById(Long id);

    Optional<ProjectSettlement> findByProjectId(Long projectId);

    List<ProjectSettlement> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId);
}
