package com.growmighty.lectures.firstday.payment.infrastructure;

import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentStatusOutboxJpaRepository extends JpaRepository<PaymentStatusOutbox, Long> {

    List<PaymentStatusOutbox> findByStatusOrderByIdAsc(PaymentStatusOutboxStatus status, Pageable pageable);

    boolean existsByPaymentIdAndPaymentStatus(Long paymentId, PaymentStatus paymentStatus);

    @Modifying
    @Query("""
      update PaymentStatusOutbox outbox
         set outbox.status = :processingStatus,
             outbox.updatedAt = CURRENT_TIMESTAMP
       where outbox.id = :id
         and outbox.status = :pendingStatus
      """)
    int claimPending(
        @Param("id") Long id,
        @Param("pendingStatus") PaymentStatusOutboxStatus pendingStatus,
        @Param("processingStatus") PaymentStatusOutboxStatus processingStatus
    );

    @Modifying
    @Query("""
      update PaymentStatusOutbox outbox
         set outbox.status = :pendingStatus,
             outbox.updatedAt = CURRENT_TIMESTAMP
       where outbox.status = :processingStatus
         and outbox.updatedAt < :cutoff
      """)
    int recoverStaleProcessing(
        @Param("cutoff") LocalDateTime cutoff,
        @Param("processingStatus") PaymentStatusOutboxStatus processingStatus,
        @Param("pendingStatus") PaymentStatusOutboxStatus pendingStatus
    );
}
