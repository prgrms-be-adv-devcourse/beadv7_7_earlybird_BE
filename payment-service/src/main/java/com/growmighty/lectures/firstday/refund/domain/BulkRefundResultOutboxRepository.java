package com.growmighty.lectures.firstday.refund.domain;

import java.util.List;

public interface BulkRefundResultOutboxRepository {
    BulkRefundResultOutbox save(BulkRefundResultOutbox outbox);

    boolean existsBySettlementIdAndResultStatus(Long settlementId, BulkRefundResultStatus resultStatus);

    List<BulkRefundResultOutbox> findPending(int limit);
}
