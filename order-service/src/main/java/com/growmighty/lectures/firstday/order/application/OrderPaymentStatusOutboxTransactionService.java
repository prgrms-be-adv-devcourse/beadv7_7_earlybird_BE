package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.dto.OrderPaymentStatusMessage;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutbox;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutboxStatus;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class OrderPaymentStatusOutboxTransactionService {
    private static final int BATCH_SIZE = 50;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

    private final OrderPaymentStatusOutboxRepository outboxRepository;

    @Transactional(readOnly = true)
    List<Long> pendingIds(Instant now) {
        return outboxRepository.findPendingIds(now, BATCH_SIZE);
    }

    @Transactional
    Optional<OrderPaymentStatusOutboxTask> claimByOrderAndStatus(Long orderId, OrderStatus status, Instant now) {
        return outboxRepository.findByOrderIdAndOrderStatus(orderId, status)
                .flatMap(outbox -> claim(outbox.getId(), now));
    }

    @Transactional
    Optional<OrderPaymentStatusOutboxTask> claim(Long outboxId, Instant now) {
        OrderPaymentStatusOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != OrderPaymentStatusOutboxStatus.PENDING
                || outboxRepository.existsEarlierPending(outbox.getOrderId(), outboxId)) {
            return Optional.empty();
        }
        if (!outboxRepository.claim(outboxId, now, now.plus(CLAIM_LEASE))) {
            return Optional.empty();
        }
        return outboxRepository.findById(outboxId).map(this::toTask);
    }

    @Transactional
    void published(Long outboxId, Instant now) {
        outboxRepository.findById(outboxId).ifPresent(outbox -> outbox.published(now));
    }

    @Transactional
    void recordFailure(Long outboxId, Instant attemptedAt, Instant retryAt, String error) {
        outboxRepository.findById(outboxId)
                .filter(outbox -> outbox.getStatus() == OrderPaymentStatusOutboxStatus.PENDING)
                .ifPresent(outbox -> outbox.recordFailure(attemptedAt, retryAt, error));
    }

    private OrderPaymentStatusOutboxTask toTask(OrderPaymentStatusOutbox outbox) {
        return new OrderPaymentStatusOutboxTask(
                outbox.getId(),
                outbox.getRetryCount(),
                new OrderPaymentStatusMessage(
                        outbox.getEventId(),
                        outbox.getOccurredAt(),
                        outbox.getOrderId(),
                        outbox.getPgOrderId(),
                        outbox.getProjectId(),
                        outbox.getPaymentAmount(),
                        outbox.getOrderStatus()
                )
        );
    }
}
