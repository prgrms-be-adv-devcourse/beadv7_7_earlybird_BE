package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.PayoutObligationJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataPayoutObligationRepository
        extends JpaRepository<PayoutObligationJpaEntity, Long> {

    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    @Query("select obligation from PayoutObligationJpaEntity obligation where obligation.id = :id")
    Optional<PayoutObligationJpaEntity> findAggregateById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"attempts", "successfulAttempt"})
    Optional<PayoutObligationJpaEntity> findBySettlementId(Long settlementId);
}
