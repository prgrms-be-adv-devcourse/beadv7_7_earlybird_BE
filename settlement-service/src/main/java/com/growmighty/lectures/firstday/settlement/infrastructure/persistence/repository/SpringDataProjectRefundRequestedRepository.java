package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProjectRefundRequestedRepository
        extends JpaRepository<ProjectRefundRequested, String> {

    Optional<ProjectRefundRequested> findByProjectId(Long projectId);

    List<ProjectRefundRequested> findTop100ByPublishedAtIsNullOrderByOccurredAt();
}
