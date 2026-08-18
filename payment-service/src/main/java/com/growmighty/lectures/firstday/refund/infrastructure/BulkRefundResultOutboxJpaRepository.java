package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultOutbox;
import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BulkRefundResultOutboxJpaRepository extends JpaRepository<BulkRefundResultOutbox, Long> {
    boolean existsBySettlementId(Long settlementId);

    List<BulkRefundResultOutbox> findByOutboxStatusOrderById(BulkRefundResultOutboxStatus outboxStatus, Pageable pageable);
}
