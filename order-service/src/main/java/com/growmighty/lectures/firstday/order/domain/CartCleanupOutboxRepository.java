package com.growmighty.lectures.firstday.order.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartCleanupOutboxRepository {
    CartCleanupOutbox save(CartCleanupOutbox outbox);

    boolean existsByOrderId(Long orderId);

    Optional<CartCleanupOutbox> findById(Long id);

    Optional<CartCleanupOutbox> findByOrderId(Long orderId);

    List<Long> findPendingIds(LocalDateTime now, int batchSize);

    boolean claim(Long id, LocalDateTime now, LocalDateTime leaseUntil);
}
