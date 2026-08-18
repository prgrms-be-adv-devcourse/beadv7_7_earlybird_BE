package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import java.util.List;
import java.util.Optional;

public interface ProjectRefundRequestedRepository {

    ProjectRefundRequested save(ProjectRefundRequested request);

    Optional<ProjectRefundRequested> findByProjectId(Long projectId);

    Optional<ProjectRefundRequested> findByRefundRequestId(String refundRequestId);

    List<ProjectRefundRequested> findAllByOrderByOccurredAtDescProjectIdDesc();

    List<ProjectRefundRequested> findPending();
}
