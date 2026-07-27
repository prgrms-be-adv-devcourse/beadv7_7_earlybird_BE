package com.growmighty.lectures.firstday.settlement.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "payout_attempts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payout_attempt_ref_payout_id", columnNames = "ref_payout_id"),
                @UniqueConstraint(name = "uk_payout_attempt_idempotency_key", columnNames = "idempotency_key"),
                @UniqueConstraint(name = "uk_payout_attempt_sequence", columnNames = {"payout_obligation_id", "sequence"})
        }
)
public class PayoutAttempt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_obligation_id", nullable = false, updatable = false)
    private PayoutObligation payoutObligation;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Column(name = "ref_payout_id", nullable = false, updatable = false, length = 50)
    private String refPayoutId;

    @Column(name = "toss_payout_id", unique = true, length = 35)
    private String tossPayoutId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 300)
    private String idempotencyKey;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "payout_amount", nullable = false, precision = 19, scale = 0))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PayoutAttemptStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected PayoutAttempt() {
    }

    private PayoutAttempt(
            PayoutObligation payoutObligation,
            int sequence,
            String refPayoutId,
            String idempotencyKey,
            Money amount,
            LocalDateTime requestedAt
    ) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("지급 시도 순번은 양수여야 합니다.");
        }
        if (refPayoutId == null || refPayoutId.isBlank()) {
            throw new IllegalArgumentException("지급대행 참조 식별자는 필수입니다.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등키는 필수입니다.");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("지급 요청 시각은 필수입니다.");
        }
        this.payoutObligation = Objects.requireNonNull(payoutObligation, "지급 의무는 필수입니다.");
        this.sequence = sequence;
        this.refPayoutId = refPayoutId;
        this.idempotencyKey = idempotencyKey;
        this.amount = Objects.requireNonNull(amount, "지급 금액은 필수입니다.");
        this.requestedAt = requestedAt;
        this.status = PayoutAttemptStatus.REQUESTED;
    }

    static PayoutAttempt requested(
            PayoutObligation payoutObligation,
            int sequence,
            String refPayoutId,
            String idempotencyKey,
            Money amount,
            LocalDateTime requestedAt
    ) {
        return new PayoutAttempt(
                payoutObligation,
                sequence,
                refPayoutId,
                idempotencyKey,
                amount,
                requestedAt
        );
    }

    public int sequence() {
        return sequence;
    }

    void fail(String tossPayoutId, String errorCode, LocalDateTime completedAt) {
        if (status == PayoutAttemptStatus.COMPLETED
                || status == PayoutAttemptStatus.FAILED
                || status == PayoutAttemptStatus.CANCELED) {
            throw new IllegalStateException("이미 종료된 지급 시도입니다: " + status);
        }
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("지급 실패 오류 코드는 필수입니다.");
        }
        this.tossPayoutId = tossPayoutId;
        this.errorCode = errorCode;
        this.completedAt = Objects.requireNonNull(completedAt, "지급 실패 확정 시각은 필수입니다.");
        this.status = PayoutAttemptStatus.FAILED;
    }

    void complete(String tossPayoutId, LocalDateTime completedAt) {
        if (status == PayoutAttemptStatus.COMPLETED
                || status == PayoutAttemptStatus.FAILED
                || status == PayoutAttemptStatus.CANCELED) {
            throw new IllegalStateException("이미 종료된 지급 시도입니다: " + status);
        }
        if (tossPayoutId == null || tossPayoutId.isBlank()) {
            throw new IllegalArgumentException("토스 지급 식별자는 필수입니다.");
        }
        this.tossPayoutId = tossPayoutId;
        this.completedAt = Objects.requireNonNull(completedAt, "지급 완료 시각은 필수입니다.");
        this.status = PayoutAttemptStatus.COMPLETED;
    }

    void markUnknown() {
        if (status != PayoutAttemptStatus.REQUESTED && status != PayoutAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("결과 불명확으로 전환할 수 없는 지급 시도입니다: " + status);
        }
        status = PayoutAttemptStatus.UNKNOWN;
    }
}
