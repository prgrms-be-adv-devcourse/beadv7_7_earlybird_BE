package com.growmighty.lectures.firstday.payment.domain;

import java.util.List;

public interface PaymentStatusOutboxRepository {

    PaymentStatusOutbox save(PaymentStatusOutbox paymentStatusOutbox);

    boolean existsByPaymentIdAndPaymentStatus(Long paymentId, PaymentStatus paymentStatus);

    List<PaymentStatusOutbox> findPending(int limit);
}
