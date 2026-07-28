package com.growmighty.lectures.firstday.settlement.domain;

import java.util.Optional;

public interface PayoutObligationRepository {

    PayoutObligation save(PayoutObligation obligation);

    Optional<PayoutObligation> findBySettlementId(Long settlementId);
}
