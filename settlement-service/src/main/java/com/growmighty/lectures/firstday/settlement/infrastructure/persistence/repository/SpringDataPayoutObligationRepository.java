package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPayoutObligationRepository extends JpaRepository<PayoutObligation, Long> {

    @Override
    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    Optional<PayoutObligation> findById(Long id);

    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    Optional<PayoutObligation> findBySettlementId(Long settlementId);

    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    List<PayoutObligation> findAllBySettlementIdIn(Collection<Long> settlementIds);
}
