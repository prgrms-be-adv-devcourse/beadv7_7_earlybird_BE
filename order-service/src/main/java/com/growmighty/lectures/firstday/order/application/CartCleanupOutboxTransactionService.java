package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.CartCleanupOutbox;
import com.growmighty.lectures.firstday.order.domain.CartCleanupOutboxRepository;
import com.growmighty.lectures.firstday.order.domain.CartCleanupStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class CartCleanupOutboxTransactionService {
    private static final int BATCH_SIZE = 50;
    private static final int CLAIM_LEASE_MINUTES = 2;

    private final CartCleanupOutboxRepository outboxRepository;

    @Transactional(readOnly = true)
    List<Long> pendingIds(LocalDateTime now) {
        return outboxRepository.findPendingIds(now, BATCH_SIZE);
    }

    @Transactional
    Optional<CartCleanupTask> claimByOrderId(Long orderId, LocalDateTime now) {
        return outboxRepository.findByOrderId(orderId)
                .flatMap(outbox -> claim(outbox.getId(), now));
    }

    @Transactional
    Optional<CartCleanupTask> claim(Long outboxId, LocalDateTime now) {
        if (!outboxRepository.claim(outboxId, now, now.plusMinutes(CLAIM_LEASE_MINUTES))) {
            return Optional.empty();
        }
        return outboxRepository.findById(outboxId).map(this::toTask);
    }

    @Transactional
    void complete(Long outboxId, LocalDateTime now) {
        outboxRepository.findById(outboxId).ifPresent(outbox -> outbox.complete(now));
    }

    @Transactional
    void recordFailure(Long outboxId, LocalDateTime attemptedAt, LocalDateTime retryAt, String error) {
        outboxRepository.findById(outboxId)
                .filter(outbox -> outbox.getStatus() == CartCleanupStatus.PENDING)
                .ifPresent(outbox -> outbox.recordFailure(attemptedAt, retryAt, error));
    }

    private CartCleanupTask toTask(CartCleanupOutbox outbox) {
        return new CartCleanupTask(outbox.getId(), outbox.getOrderId(), outbox.getUserId(),
                List.copyOf(outbox.getRewardIds()), outbox.getCleanupType(), outbox.getRetryCount());
    }
}
