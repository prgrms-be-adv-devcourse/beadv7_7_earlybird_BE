package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundJpaRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByPaymentId(Long paymentId);

    @Query("""
          select refund.paymentId
          from Refund refund
          where refund.paymentId in :paymentIds
          """)
    List<Long> findPaymentIdsByPaymentIdIn(@Param("paymentIds") List<Long> paymentIds);


    @Query("""
          select refund.id
          from Refund refund
          where refund.status = :plannedStatus
             or (
                 refund.status = :retryPendingStatus
                 and refund.nextRetryAt <= :now
             )
          order by refund.createdAt asc
          """)
    List<Long> findNextCancelableRefundId(@Param("plannedStatus") RefundStatus plannedStatus,
                                          @Param("retryPendingStatus") RefundStatus retryPendingStatus,
                                          @Param("now") LocalDateTime now,
                                          Pageable pageable);

    @Query("""
      select new com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget(
          refund.id,
          payment.paymentKey
      )
      from Refund refund
      join Payment payment on payment.paymentId = refund.paymentId
      where refund.status = :status
        and refund.createdAt < :cutoff
      order by refund.createdAt asc
      """)
    List<RefundRecoveryTarget> findTimedOutRequestedTargets(
        @Param("status") RefundStatus status,
        @Param("cutoff") LocalDateTime cutoff,
        Pageable pageable
    );


    @Query("""
      select count(refund) > 0
      from Refund refund
      where refund.settlementId = :settlementId
        and refund.status in :statuses
      """)
    boolean existsBySettlementIdAndStatusIn(
        @Param("settlementId") Long settlementId,
        @Param("statuses") List<RefundStatus> status
    );

    @Query("""
      select payment.orderId
      from Refund refund
      join Payment payment on payment.paymentId = refund.paymentId
      where refund.settlementId = :settlementId
      order by refund.id asc
      """)
    List<Long> findOrderIdsBySettlementId(@Param("settlementId") Long settlementId);
}
