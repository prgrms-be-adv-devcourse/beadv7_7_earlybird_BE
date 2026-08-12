package com.growmighty.lectures.firstday.order.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cart_cleanup_outboxes", uniqueConstraints = @UniqueConstraint(
        name = "uk_cart_cleanup_outboxes_order_id", columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartCleanupOutbox extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanup_type", nullable = false, updatable = false)
    private CartCleanupType cleanupType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cart_cleanup_outbox_reward_ids",
            joinColumns = @JoinColumn(name = "outbox_id"))
    @OrderColumn(name = "reward_order")
    @Column(name = "reward_id", nullable = false)
    private final List<Long> rewardIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartCleanupStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private CartCleanupOutbox(Long orderId, Long userId, List<Long> rewardIds,
                              CartCleanupType cleanupType, LocalDateTime now) {
        if (orderId == null || userId == null || rewardIds == null || rewardIds.isEmpty()) {
            throw new IllegalArgumentException("Cart cleanup snapshot is required.");
        }
        if (cleanupType == null) {
            throw new IllegalArgumentException("Cart cleanup type is required.");
        }
        if (cleanupType == CartCleanupType.INVALID_REWARD && rewardIds.size() != 1) {
            throw new IllegalArgumentException("Invalid-reward cleanup requires exactly one reward.");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.rewardIds.addAll(rewardIds);
        this.cleanupType = cleanupType;
        this.status = CartCleanupStatus.PENDING;
        this.nextRetryAt = now;
    }

    public static CartCleanupOutbox pendingPaidOrder(Long orderId, Long userId, List<Long> rewardIds,
                                                     LocalDateTime now) {
        return new CartCleanupOutbox(orderId, userId, rewardIds, CartCleanupType.PAID_ORDER, now);
    }

    public static CartCleanupOutbox pendingInvalidReward(Long orderId, Long userId, Long rewardId,
                                                         LocalDateTime now) {
        return new CartCleanupOutbox(orderId, userId, List.of(rewardId), CartCleanupType.INVALID_REWARD, now);
    }

    public void complete(LocalDateTime now) {
        if (status == CartCleanupStatus.COMPLETED) {
            return;
        }
        status = CartCleanupStatus.COMPLETED;
        lastAttemptAt = now;
        completedAt = now;
        lastError = null;
    }

    public void recordFailure(LocalDateTime attemptedAt, LocalDateTime retryAt, String error) {
        if (status == CartCleanupStatus.COMPLETED) {
            return;
        }
        retryCount++;
        lastAttemptAt = attemptedAt;
        nextRetryAt = retryAt;
        lastError = error;
    }
}
