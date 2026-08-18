package com.growmighty.lectures.firstday.refund.domain;

import java.util.List;

public interface BulkRefundResultOutboxRepository {
    BulkRefundResultOutbox save(BulkRefundResultOutbox outbox);

    boolean existsBySettlementId(Long settlementId);

    List<BulkRefundResultOutbox> findPending(int limit);
}
