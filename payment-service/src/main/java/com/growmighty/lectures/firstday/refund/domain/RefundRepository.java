package com.growmighty.lectures.firstday.refund.domain;

import java.util.List;
import java.util.Optional;

public interface RefundRepository {
    Refund save(Refund refund);

    Optional<Refund> findById(Long id);

    List<Refund> findByPaymentId(Long paymentId);
}
