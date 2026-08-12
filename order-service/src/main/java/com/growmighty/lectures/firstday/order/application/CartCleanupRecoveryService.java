package com.growmighty.lectures.firstday.order.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartCleanupRecoveryService {
    private static final Duration MAX_TECHNICAL_BACKOFF = Duration.ofHours(1);
    private static final Duration NON_RETRYABLE_BACKOFF = Duration.ofHours(24);

    private final CartCleanupOutboxTransactionService transactions;
    private final OrderCartHandler cartHandler;
    private final OrderRemoteCallExecutor remoteCalls;

    public void cleanupImmediately(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        transactions.claimByOrderId(orderId, now).ifPresent(this::attempt);
    }

    public void recoverPendingCleanups() {
        LocalDateTime now = LocalDateTime.now();
        for (Long outboxId : transactions.pendingIds(now)) {
            try {
                transactions.claim(outboxId, now).ifPresent(this::attempt);
            } catch (OptimisticLockingFailureException conflict) {
                log.info("cart cleanup recovery skipped after concurrent update. outboxId={}", outboxId);
            } catch (RuntimeException failure) {
                log.warn("cart cleanup recovery could not claim Outbox. outboxId={}", outboxId, failure);
            }
        }
    }

    private void attempt(CartCleanupTask task) {
        LocalDateTime attemptedAt = LocalDateTime.now();
        try {
            switch (task.cleanupType()) {
                case PAID_ORDER -> cartHandler.removeCompletedOrderItems(task.userId(), task.rewardIds());
                case INVALID_REWARD -> cartHandler.removeInvalidRewardItems(task.userId(), task.rewardIds());
            }
            transactions.complete(task.outboxId(), LocalDateTime.now());
            log.info("cart cleanup completed. outboxId={}, orderId={}, userId={}, cleanupType={}, rewardIds={}",
                    task.outboxId(), task.orderId(), task.userId(), task.cleanupType(), task.rewardIds());
        } catch (RuntimeException failure) {
            boolean technical = remoteCalls.isTechnical(failure);
            Duration backoff = technical ? technicalBackoff(task.retryCount()) : NON_RETRYABLE_BACKOFF;
            try {
                transactions.recordFailure(task.outboxId(), attemptedAt, attemptedAt.plus(backoff), error(failure));
            } catch (OptimisticLockingFailureException conflict) {
                log.info("cart cleanup failure metadata skipped after concurrent completion. outboxId={}",
                        task.outboxId());
                return;
            }
            log.warn("cart cleanup remains pending. outboxId={}, orderId={}, retryable={}, nextRetryIn={}",
                    task.outboxId(), task.orderId(), technical, backoff, failure);
        }
    }

    private Duration technicalBackoff(int retryCount) {
        long multiplier = 1L << Math.min(retryCount, 7);
        Duration calculated = Duration.ofSeconds(30L * multiplier);
        return calculated.compareTo(MAX_TECHNICAL_BACKOFF) > 0 ? MAX_TECHNICAL_BACKOFF : calculated;
    }

    private String error(RuntimeException failure) {
        String message = Optional.ofNullable(failure.getMessage()).orElse(failure.getClass().getName());
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
