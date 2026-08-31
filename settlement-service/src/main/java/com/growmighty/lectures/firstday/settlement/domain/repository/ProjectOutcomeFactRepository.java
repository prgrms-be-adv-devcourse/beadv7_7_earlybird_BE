package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import java.util.Collection;
import java.util.List;

public interface ProjectOutcomeFactRepository {

    List<ProjectOutcomeFact> findAllByProjectIdIn(Collection<Long> projectIds);

    List<ProjectOutcomeFact> findAllByCreatorIdOrderByOccurredAtDescProjectIdDesc(Long creatorId);
}
