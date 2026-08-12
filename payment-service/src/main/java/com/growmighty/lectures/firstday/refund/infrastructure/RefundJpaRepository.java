package com.growmighty.lectures.firstday.refund.infrastructure;

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
        select refund.id
        from Refund refund
        where refund.status = :status
          and refund.createdAt < :cutoff
        order by refund.createdAt asc
        """)
    List<Long> findRecoveryTargetIds(
        @Param("status") RefundStatus status,
        @Param("cutoff") LocalDateTime cutoff,
        Pageable pageable);

    @Query("""
      select refund.id
      from Refund refund
      where refund.status = :status
      order by refund.createdAt asc
      """)
    List<Long> findRefundIdsByStatus(@Param("status") RefundStatus status, Pageable pageable);
}
