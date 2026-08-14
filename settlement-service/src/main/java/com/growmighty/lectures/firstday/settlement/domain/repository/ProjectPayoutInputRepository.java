package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import java.util.List;

public interface ProjectPayoutInputRepository {

    List<ProjectOutcomeFact> findSucceededProjects();

    List<OrderPaymentFact> findCompletedPaymentsByProjectId(Long projectId);
}
