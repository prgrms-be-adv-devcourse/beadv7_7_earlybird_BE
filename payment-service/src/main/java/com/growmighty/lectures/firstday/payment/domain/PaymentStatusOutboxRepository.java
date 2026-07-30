package com.growmighty.lectures.firstday.payment.domain;

import java.util.List;

public interface PaymentStatusOutboxRepository {

    PaymentStatusOutbox save(PaymentStatusOutbox paymentStatusOutbox);

    List<PaymentStatusOutbox> findPending(int limit);
}
