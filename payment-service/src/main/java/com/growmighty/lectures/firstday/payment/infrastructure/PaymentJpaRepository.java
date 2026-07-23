package com.growmighty.lectures.firstday.payment.infrastructure;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPgOrderId(String pgOrderId);

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findByStatusAndConfirmingAtBeforeOrderByConfirmingAtAsc(PaymentStatus status, LocalDateTime cutoff, Pageable pageable);
}
