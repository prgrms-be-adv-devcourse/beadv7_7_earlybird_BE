package com.growmighty.lectures.firstday.refund.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundRepository {
    Refund save(Refund refund);

    Optional<Refund> findById(Long id);

    Optional<Refund> findByPaymentId(Long paymentId);

    List<Long> findExistingPaymentIds(List<Long> paymentIds);

    Optional<Long> findNextCancelableRefundId(LocalDateTime now);

    boolean existsInProgressBySettlementId(Long settlementId);

    boolean existsFailedBySettlementId(Long settlementId);

    List<Long> findOrderIdsBySettlementId(Long settlementId);
}
