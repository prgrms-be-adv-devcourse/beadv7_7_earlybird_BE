package com.growmighty.lectures.firstday.payment.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    Optional<Payment> findByOrderId(UUID orderId);

    List<Long> findConfirmingPaymentIdsBefore(LocalDateTime cutoff, int limit);

    Optional<Payment> findByPaymentKey(String paymentKey);

}
