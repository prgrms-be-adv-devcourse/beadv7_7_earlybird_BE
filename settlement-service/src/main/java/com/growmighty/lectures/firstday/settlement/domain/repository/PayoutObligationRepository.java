// TODO(settlement-plan): Provide idempotent obligation and attempt lookup needed to prevent duplicate scheduled or manual payouts.
package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import java.util.List;
import java.util.Optional;

public interface PayoutObligationRepository {

    PayoutObligation save(PayoutObligation obligation);

    Optional<PayoutObligation> findById(Long id);

    Optional<PayoutObligation> findBySettlementId(Long settlementId);

    List<PayoutObligation> findAllBySettlementIdIn(List<Long> settlementIds);
}
