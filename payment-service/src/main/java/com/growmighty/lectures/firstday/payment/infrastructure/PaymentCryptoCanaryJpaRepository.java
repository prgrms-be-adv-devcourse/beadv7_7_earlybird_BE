package com.growmighty.lectures.firstday.payment.infrastructure;

import com.growmighty.lectures.firstday.payment.domain.PaymentCryptoCanary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCryptoCanaryJpaRepository extends JpaRepository<PaymentCryptoCanary, Long> {
}
