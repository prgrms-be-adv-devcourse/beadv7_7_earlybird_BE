// TODO(settlement-plan): Concentrate obligation and attempt rehydration here and enforce duplicate-payout lookup semantics.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.PayoutObligationJpaEntity;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataPayoutObligationRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PayoutObligationRepositoryAdapter implements PayoutObligationRepository {

    private final SpringDataPayoutObligationRepository repository;

    @Override
    @Transactional
    public PayoutObligation save(PayoutObligation obligation) {
        PayoutObligationJpaEntity entity;
        if (obligation.id() == null) {
            entity = PayoutObligationJpaEntity.fromDomain(obligation);
        } else {
            entity = repository.findAggregateById(obligation.id())
                    .orElseThrow(() -> new IllegalStateException("저장된 지급 의무가 존재하지 않습니다."));
            if (!Objects.equals(entity.version(), obligation.version())) {
                throw new ObjectOptimisticLockingFailureException(PayoutObligation.class, obligation.id());
            }
            entity.sync(obligation);
        }
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PayoutObligation> findById(Long id) {
        return repository.findAggregateById(id).map(PayoutObligationJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PayoutObligation> findBySettlementId(Long settlementId) {
        return repository.findBySettlementId(settlementId).map(PayoutObligationJpaEntity::toDomain);
    }
}
