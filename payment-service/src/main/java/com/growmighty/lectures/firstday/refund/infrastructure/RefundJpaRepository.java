package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundJpaRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByPaymentId(Long paymentId);
}
