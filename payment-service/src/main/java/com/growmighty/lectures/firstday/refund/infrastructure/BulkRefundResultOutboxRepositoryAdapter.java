package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultOutbox;
import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultOutboxRepository;
import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultOutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BulkRefundResultOutboxRepositoryAdapter implements BulkRefundResultOutboxRepository {

    private final BulkRefundResultOutboxJpaRepository jpaRepository;

    @Override
    public BulkRefundResultOutbox save(BulkRefundResultOutbox outbox) {
        return jpaRepository.save(outbox);
    }

    @Override
    public boolean existsBySettlementId(Long settlementId) {
        return jpaRepository.existsBySettlementId(settlementId);
    }

    @Override
    public List<BulkRefundResultOutbox> findPending(int limit) {
        return jpaRepository.findByOutboxStatusOrderById(BulkRefundResultOutboxStatus.PENDING, PageRequest.of(0, limit));
    }
}
