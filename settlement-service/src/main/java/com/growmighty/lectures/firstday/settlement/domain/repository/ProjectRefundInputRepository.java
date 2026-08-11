package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import java.time.Instant;
import java.util.List;

public interface ProjectRefundInputRepository {

    List<ProjectOutcomeFact> findRefundOutcomes(Instant dueBefore, int limit);

    List<OrderPaymentFact> findPayments(Long projectId);
}
