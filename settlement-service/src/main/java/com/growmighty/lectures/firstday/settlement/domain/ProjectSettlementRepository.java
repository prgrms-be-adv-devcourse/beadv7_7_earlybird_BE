package com.growmighty.lectures.firstday.settlement.domain;

import java.util.Optional;

public interface ProjectSettlementRepository {

    ProjectSettlement save(ProjectSettlement settlement);

    Optional<ProjectSettlement> findById(Long id);

    Optional<ProjectSettlement> findByProjectId(Long projectId);
}
