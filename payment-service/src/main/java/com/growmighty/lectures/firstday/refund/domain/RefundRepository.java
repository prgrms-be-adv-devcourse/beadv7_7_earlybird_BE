package com.growmighty.lectures.firstday.refund.domain;

import java.util.Optional;

public interface RefundRepository {
    Refund save(Refund refund);

    Optional<Refund> findById(Long id);

    Optional<Refund> findByPaymentId(Long paymentId);
}
