package com.growmighty.lectures.firstday.payment.infrastructure;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
}
