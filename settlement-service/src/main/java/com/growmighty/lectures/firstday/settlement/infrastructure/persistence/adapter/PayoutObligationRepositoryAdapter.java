package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataPayoutObligationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PayoutObligationRepositoryAdapter implements PayoutObligationRepository {

    private final SpringDataPayoutObligationRepository repository;

    @Override
    @Transactional
    public PayoutObligation save(PayoutObligation payoutObligation) {
        return repository.saveAndFlush(payoutObligation);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PayoutObligation> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PayoutObligation> findBySettlementId(Long settlementId) {
        return repository.findBySettlementId(settlementId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutObligation> findAllBySettlementIdIn(Collection<Long> settlementIds) {
        return repository.findAllBySettlementIdIn(settlementIds);
    }
}
