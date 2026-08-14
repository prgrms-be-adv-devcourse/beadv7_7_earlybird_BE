package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.dto.OrderPaymentStatusMessage;
import com.growmighty.lectures.firstday.order.application.port.OrderPaymentStatusEventPublisher;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentStatusOutboxRecoveryService {
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final OrderPaymentStatusOutboxTransactionService transactions;
    private final OrderPaymentStatusEventPublisher eventPublisher;

    public void publishImmediately(Long orderId, OrderStatus status) {
        Instant now = Instant.now();
        try {
            transactions.claimByOrderAndStatus(orderId, status, now).ifPresent(this::attempt);
        } catch (RuntimeException failure) {
            log.warn("immediate payment status publication deferred to recovery. orderId={}, orderStatus={}",
                    orderId, status, failure);
        }
    }

    public void publishPending() {
        Instant now = Instant.now();
        for (Long outboxId : transactions.pendingIds(now)) {
            try {
                transactions.claim(outboxId, now).ifPresent(this::attempt);
            } catch (OptimisticLockingFailureException conflict) {
                log.info("payment status Outbox recovery skipped after concurrent update. outboxId={}", outboxId);
            } catch (RuntimeException failure) {
                log.warn("payment status Outbox recovery could not claim record. outboxId={}", outboxId, failure);
            }
        }
    }

    private void attempt(OrderPaymentStatusOutboxTask task) {
        OrderPaymentStatusMessage message = task.message();
        Instant attemptedAt = Instant.now();
        try {
            eventPublisher.publish(message);
            transactions.published(task.outboxId(), Instant.now());
            log.info("payment status event published. eventId={}, orderId={}, orderStatus={}",
                    message.eventId(), message.orderId(), message.orderStatus());
        } catch (RuntimeException failure) {
            Duration backoff = backoff(task.retryCount());
            try {
                transactions.recordFailure(
                        task.outboxId(), attemptedAt, attemptedAt.plus(backoff), error(failure));
            } catch (OptimisticLockingFailureException conflict) {
                log.info("payment status failure metadata skipped after concurrent publication. eventId={}",
                        message.eventId());
                return;
            } catch (RuntimeException metadataFailure) {
                log.warn("payment status failure metadata could not be recorded; lease recovery remains active. eventId={}",
                        message.eventId(), metadataFailure);
                return;
            }
            log.warn("payment status event remains pending. eventId={}, orderId={}, nextRetryIn={}",
                    message.eventId(), message.orderId(), backoff, failure);
        }
    }

    private Duration backoff(int retryCount) {
        long multiplier = 1L << Math.min(retryCount, 7);
        Duration calculated = Duration.ofSeconds(30L * multiplier);
        return calculated.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : calculated;
    }

    private String error(RuntimeException failure) {
        String message = Optional.ofNullable(failure.getMessage()).orElse(failure.getClass().getName());
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
