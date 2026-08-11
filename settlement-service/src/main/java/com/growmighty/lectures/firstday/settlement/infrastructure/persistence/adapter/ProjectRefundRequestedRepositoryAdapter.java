package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectRefundRequestedRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectRefundRequestedRepositoryAdapter
        implements ProjectRefundRequestedRepository {

    private final SpringDataProjectRefundRequestedRepository repository;

    @Override
    @Transactional
    public ProjectRefundRequested save(ProjectRefundRequested request) {
        return repository.saveAndFlush(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectRefundRequested> findByProjectId(Long projectId) {
        return repository.findByProjectId(projectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectRefundRequested> findPending() {
        return repository.findTop100ByPublishedAtIsNullOrderByOccurredAt();
    }
}
