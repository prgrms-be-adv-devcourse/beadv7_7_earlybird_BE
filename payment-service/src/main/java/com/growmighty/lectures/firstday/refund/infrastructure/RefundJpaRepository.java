package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundJpaRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByPaymentId(Long paymentId);
}
