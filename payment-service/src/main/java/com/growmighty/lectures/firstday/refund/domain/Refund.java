package com.growmighty.lectures.firstday.refund.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.payment.domain.vo.SensitiveValue;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/** 일괄 환불 추적 — reason/status 로 배치 실패·재시도를 추적한다. */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long paymentId;

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(name = "cancel_idempotency_key", nullable = false, length = 512)
    private SensitiveValue cancelIdempotencyKey;

    /** 실패 시 null — 재시도 대상 */
    @Column
    private LocalDateTime completedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    private Refund(Long paymentId, Long settlementId, BigDecimal amount, RefundReason reason, RefundStatus status) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("환불 금액은 0원보다 커야 합니다. 입력값: " + amount);
        }
        this.paymentId = paymentId;
        this.settlementId = settlementId;
        this.amount = amount;
        this.reason = reason;
        this.cancelIdempotencyKey = new SensitiveValue(UUID.randomUUID().toString()); // <-- 민감 값 VO로 저장
        this.status = status;
        this.retryCount = 0;
    }

    public static Refund planned(
        Long paymentId,
        Long settlementId,
        BigDecimal amount,
        RefundReason reason
    ) {
        return new Refund(paymentId, settlementId, amount, reason, RefundStatus.PLANNED);
    }

    public static Refund request(Long paymentId, BigDecimal amount, RefundReason reason) {
        return new Refund(paymentId,null, amount, reason, RefundStatus.REQUESTED);
    }

    public boolean isRequested() {
        return RefundStatus.REQUESTED == this.status;
    }


    // 일괄 환불의 PG 취소 요청의 시작부
    public void startRequest() {
        if (this.status != RefundStatus.PLANNED && this.status != RefundStatus.RETRY_PENDING) {
            throw new IllegalStateException("PLANNED 또는 RETRY_PENDING 상태의 환불만 요청할 수 있습니다. status = " +  this.status);
        }

        this.status = RefundStatus.REQUESTED;
        this.nextRetryAt = null;
    }

    public void complete() {
        if (!isRequested()) {
            throw new IllegalStateException("REQUESTED 상태의 환불만 완료할 수 있습니다. status = " + this.status);
        }
        this.status = RefundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void scheduleRetry(
        LocalDateTime now,
        int maximumRetryCount,
        Duration retryDelay
    ) {
        if (!isRequested()) {
            throw new IllegalStateException("REQUESTED 상태의 환불만 재시도할 수 있습니다. status = " + this.status);
        }

        if (this.retryCount >= maximumRetryCount) {
            this.status = RefundStatus.FAILED;
            return;
        }

        this.retryCount++;
        this.nextRetryAt = now.plus(retryDelay);
        this.status = RefundStatus.RETRY_PENDING;
    }

    // 추가 : 정합화 경합 시 이미 처리된 환불 실패 전이는 무시
    public boolean reconcileFailed() {
        if (!isRequested()) {
            return false;
        }

        this.status = RefundStatus.FAILED;
        return true;
    }
}
