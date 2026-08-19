package com.growmighty.lectures.firstday.refund.domain;

import java.util.List;

public interface BulkRefundResultOutboxRepository {
    BulkRefundResultOutbox save(BulkRefundResultOutbox outbox);

    List<BulkRefundResultOutbox> findPending(int limit);

    //같은 결과 outbox가 없을 때만 저장하는 메서드
    void insertIfAbsent(
        Long refundRequestId,
        BulkRefundResultStatus resultStatus
    );
}
