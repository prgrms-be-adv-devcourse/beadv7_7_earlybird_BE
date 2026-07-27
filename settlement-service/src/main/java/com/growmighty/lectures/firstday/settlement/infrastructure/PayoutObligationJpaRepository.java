package com.growmighty.lectures.firstday.settlement.infrastructure;

import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutObligationJpaRepository extends JpaRepository<PayoutObligation, Long> {

    Optional<PayoutObligation> findBySettlementId(Long settlementId);
}
