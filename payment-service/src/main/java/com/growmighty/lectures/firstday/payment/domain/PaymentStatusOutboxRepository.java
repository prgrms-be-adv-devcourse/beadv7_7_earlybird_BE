package com.growmighty.lectures.firstday.payment.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentStatusOutboxRepository {

    PaymentStatusOutbox save(PaymentStatusOutbox paymentStatusOutbox);

    boolean existsByPaymentIdAndPaymentStatus(Long paymentId, PaymentStatus paymentStatus);

    List<PaymentStatusOutbox> findPending(int limit);

    boolean claimPending(Long outboxId);

    int recoverStaleProcessing(LocalDateTime cutoff);
}
