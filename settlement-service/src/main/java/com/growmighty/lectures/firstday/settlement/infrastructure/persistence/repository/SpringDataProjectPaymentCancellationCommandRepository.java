package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.ProjectPaymentCancellationCommandJpaEntity;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProjectPaymentCancellationCommandRepository
        extends JpaRepository<ProjectPaymentCancellationCommandJpaEntity, Long> {

    List<ProjectPaymentCancellationCommandJpaEntity> findAllByProjectIdIn(Set<Long> projectIds);
}
