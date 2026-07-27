package com.growmighty.lectures.firstday.settlement.infrastructure;

import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSettlementJpaRepository extends JpaRepository<ProjectSettlement, Long> {

    Optional<ProjectSettlement> findByProjectId(Long projectId);
}
