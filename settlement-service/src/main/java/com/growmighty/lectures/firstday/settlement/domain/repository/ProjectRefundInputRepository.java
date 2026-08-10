package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import java.util.List;

public interface ProjectRefundInputRepository {

    List<ProjectOutcomeFact> findRefundOutcomes();

    List<OrderPaymentFact> findPayments(Long projectId);
}
