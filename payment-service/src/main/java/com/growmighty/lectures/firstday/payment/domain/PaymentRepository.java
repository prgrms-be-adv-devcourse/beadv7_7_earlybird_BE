package com.growmighty.lectures.firstday.payment.domain;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    Optional<Payment> findByOrderId(Long orderId);

}
