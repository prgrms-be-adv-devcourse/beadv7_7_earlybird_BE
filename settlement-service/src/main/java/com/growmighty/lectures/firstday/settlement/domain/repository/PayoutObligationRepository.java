package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PayoutObligationRepository {

    PayoutObligation save(PayoutObligation payoutObligation);

    Optional<PayoutObligation> findById(Long id);

    Optional<PayoutObligation> findBySettlementId(Long settlementId);

    List<PayoutObligation> findAllBySettlementIdIn(Collection<Long> settlementIds);
}
