package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.CartCleanupOutbox;
import com.growmighty.lectures.firstday.order.domain.CartCleanupOutboxRepository;
import com.growmighty.lectures.firstday.order.domain.CartCleanupStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartCleanupOutboxRepositoryAdapter implements CartCleanupOutboxRepository {
    private final CartCleanupOutboxJpaRepository jpaRepository;

    @Override
    public CartCleanupOutbox save(CartCleanupOutbox outbox) {
        return jpaRepository.save(outbox);
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        return jpaRepository.existsByOrderId(orderId);
    }

    @Override
    public Optional<CartCleanupOutbox> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<CartCleanupOutbox> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<Long> findPendingIds(LocalDateTime now, int batchSize) {
        return jpaRepository.findPendingIds(CartCleanupStatus.PENDING, now, PageRequest.of(0, batchSize));
    }

    @Override
    public boolean claim(Long id, LocalDateTime now, LocalDateTime leaseUntil) {
        return jpaRepository.claim(id, CartCleanupStatus.PENDING, now, leaseUntil) == 1;
    }
}
