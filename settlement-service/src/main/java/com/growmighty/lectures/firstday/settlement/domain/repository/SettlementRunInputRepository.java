package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import java.time.Instant;
import java.util.List;

public interface SettlementRunInputRepository {

    List<ProjectOutcomeFact> findProjectOutcomes();

    List<OrderPaymentFact> findCompletedPayments(Instant startInclusive, Instant endExclusive);
}
