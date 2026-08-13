package com.growmighty.lectures.firstday.payment.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findAllPaidByOrderIds(List<Long> orderIds);

    List<Long> findConfirmingPaymentIdsBefore(LocalDateTime cutoff, int limit);

}
