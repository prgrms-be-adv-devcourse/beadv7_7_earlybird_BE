package com.growmighty.lectures.firstday.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderPaymentStatusOutboxRepository {
    OrderPaymentStatusOutbox save(OrderPaymentStatusOutbox outbox);

    boolean existsByOrderIdAndOrderStatus(Long orderId, OrderStatus orderStatus);

    Optional<OrderPaymentStatusOutbox> findById(Long id);

    Optional<OrderPaymentStatusOutbox> findByOrderIdAndOrderStatus(Long orderId, OrderStatus orderStatus);

    boolean existsEarlierPending(Long orderId, Long id);

    List<Long> findPendingIds(Instant now, int batchSize);

    boolean claim(Long id, Instant now, Instant leaseUntil);
}
