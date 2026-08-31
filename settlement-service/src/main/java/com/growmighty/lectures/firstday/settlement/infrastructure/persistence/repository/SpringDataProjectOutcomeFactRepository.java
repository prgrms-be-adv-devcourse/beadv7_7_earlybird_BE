package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataProjectOutcomeFactRepository extends JpaRepository<ProjectOutcomeFact, Long> {

    List<ProjectOutcomeFact> findAllByOrderByProjectId();

    List<ProjectOutcomeFact> findAllByOutcomeOrderByProjectId(ProjectOutcomeFact.Outcome outcome);

    List<ProjectOutcomeFact> findAllByCreatorIdOrderByOccurredAtDescProjectIdDesc(Long creatorId);

    @Query("""
            select outcome
            from ProjectOutcomeFact outcome
            where outcome.outcome in :outcomes
              and outcome.occurredAt < :dueBefore
              and not exists (
                  select 1
                  from ProjectRefundRequested request
                  where request.projectId = outcome.projectId
              )
            order by outcome.occurredAt, outcome.projectId
            """)
    List<ProjectOutcomeFact> findRefundOutcomesDueBefore(
            @Param("outcomes") List<ProjectOutcomeFact.Outcome> outcomes,
            @Param("dueBefore") Instant dueBefore,
            Pageable pageable
    );
}
