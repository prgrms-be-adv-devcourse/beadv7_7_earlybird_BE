package com.growmighty.lectures.firstday.payment.infrastructure;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPgOrderId(String pgOrderId);

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findAllByOrderIdInAndStatus(List<Long> orderIds, PaymentStatus status);

    @Query("""
        select payment.paymentId
        from Payment payment
        where payment.status = :status
          and payment.confirmingAt < :cutoff
        order by payment.confirmingAt asc
        """)
    List<Long> findIdsByStatusAndConfirmingAtBeforeOrderByConfirmingAtAsc(
        @Param("status") PaymentStatus status,
        @Param("cutoff") LocalDateTime cutoff,
        Pageable pageable
    );
}
