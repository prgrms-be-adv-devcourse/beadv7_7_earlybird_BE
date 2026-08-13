// TODO(settlement-plan): Persist stable refPayoutId and the minimum Toss response needed for idempotent retries and audit.
package com.growmighty.lectures.firstday.settlement.domain.model;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
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
                @UniqueConstraint(name = "uk_payout_attempt_sequence", columnNames = {"settlement_id", "sequence"})
        }
)
public class PayoutAttempt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false, updatable = false)
    private ProjectSettlement settlement;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Column(name = "ref_payout_id", nullable = false, updatable = false, length = 50)
    private String refPayoutId;

    @Column(name = "toss_payout_id", unique = true, length = 35)
    private String tossPayoutId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 300)
    private String idempotencyKey;

    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 0, updatable = false)
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
            ProjectSettlement settlement,
            int sequence,
            String refPayoutId,
            String tossPayoutId,
            String idempotencyKey,
            Money amount,
            PayoutAttemptStatus status,
            String errorCode,
            LocalDateTime requestedAt,
            LocalDateTime completedAt
    ) {
        this.settlement = Objects.requireNonNull(settlement, "프로젝트 정산은 필수입니다.");
        this.sequence = sequence;
        this.refPayoutId = refPayoutId;
        this.tossPayoutId = tossPayoutId;
        this.idempotencyKey = idempotencyKey;
        this.amount = Objects.requireNonNull(amount, "지급 금액은 필수입니다.");
        this.status = Objects.requireNonNull(status, "지급 시도 상태는 필수입니다.");
        this.errorCode = errorCode;
        this.requestedAt = Objects.requireNonNull(requestedAt, "지급 요청 시각은 필수입니다.");
        this.completedAt = completedAt;
        validateState();
    }

    static PayoutAttempt requested(
            ProjectSettlement settlement,
            int sequence,
            String refPayoutId,
            String idempotencyKey,
            Money amount,
            LocalDateTime requestedAt
    ) {
        return new PayoutAttempt(
                settlement,
                sequence,
                refPayoutId,
                null,
                idempotencyKey,
                amount,
                PayoutAttemptStatus.REQUESTED,
                null,
                requestedAt,
                null
        );
    }

    public Long id() {
        return id;
    }

    public int sequence() {
        return sequence;
    }

    public String refPayoutId() {
        return refPayoutId;
    }

    public String tossPayoutId() {
        return tossPayoutId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Money amount() {
        return amount;
    }

    public PayoutAttemptStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public LocalDateTime requestedAt() {
        return requestedAt;
    }

    public LocalDateTime completedAt() {
        return completedAt;
    }

    void fail(String tossPayoutId, String errorCode, LocalDateTime completedAt) {
        requireNotFinished();
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("지급 실패 오류 코드는 필수입니다.");
        }
        bindTossPayoutIdIfPresent(tossPayoutId);
        this.errorCode = errorCode;
        this.completedAt = Objects.requireNonNull(completedAt, "지급 실패 확정 시각은 필수입니다.");
        this.status = PayoutAttemptStatus.FAILED;
    }

    void acknowledge(String tossPayoutId, PayoutAttemptStatus acknowledgedStatus) {
        requireNotFinished();
        if (acknowledgedStatus != PayoutAttemptStatus.REQUESTED
                && acknowledgedStatus != PayoutAttemptStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("접수 상태는 REQUESTED 또는 IN_PROGRESS여야 합니다.");
        }
        bindRequiredTossPayoutId(tossPayoutId);
        this.status = acknowledgedStatus;
        this.errorCode = null;
    }

    void complete(String tossPayoutId, LocalDateTime completedAt) {
        requireNotFinished();
        bindRequiredTossPayoutId(tossPayoutId);
        this.completedAt = Objects.requireNonNull(completedAt, "지급 완료 시각은 필수입니다.");
        this.status = PayoutAttemptStatus.COMPLETED;
        this.errorCode = null;
    }

    void cancel(String tossPayoutId, LocalDateTime completedAt) {
        requireNotFinished();
        bindRequiredTossPayoutId(tossPayoutId);
        this.completedAt = Objects.requireNonNull(completedAt, "지급 취소 확정 시각은 필수입니다.");
        this.status = PayoutAttemptStatus.CANCELED;
        this.errorCode = null;
    }

    void markUnknown(String errorCode) {
        if (status == PayoutAttemptStatus.UNKNOWN) {
            if (errorCode != null && !errorCode.isBlank()) {
                this.errorCode = errorCode;
            }
            return;
        }
        if (status != PayoutAttemptStatus.REQUESTED && status != PayoutAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("결과 불명확으로 전환할 수 없는 지급 시도입니다: " + status);
        }
        if (errorCode != null && !errorCode.isBlank()) {
            this.errorCode = errorCode;
        }
        status = PayoutAttemptStatus.UNKNOWN;
    }

    private void bindRequiredTossPayoutId(String tossPayoutId) {
        if (tossPayoutId == null || tossPayoutId.isBlank()) {
            throw new IllegalArgumentException("토스 지급 식별자는 필수입니다.");
        }
        bindTossPayoutIdIfPresent(tossPayoutId);
    }

    private void bindTossPayoutIdIfPresent(String tossPayoutId) {
        if (tossPayoutId == null || tossPayoutId.isBlank()) {
            return;
        }
        if (this.tossPayoutId != null && !this.tossPayoutId.equals(tossPayoutId)) {
            throw new IllegalStateException("동일한 지급 시도에 다른 토스 지급 식별자를 반영할 수 없습니다.");
        }
        this.tossPayoutId = tossPayoutId;
    }

    private void requireNotFinished() {
        if (status == PayoutAttemptStatus.COMPLETED
                || status == PayoutAttemptStatus.FAILED
                || status == PayoutAttemptStatus.CANCELED) {
            throw new IllegalStateException("이미 종료된 지급 시도입니다: " + status);
        }
    }

    @PostLoad
    private void validateState() {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("지급 시도 식별자는 양수여야 합니다.");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("지급 시도 순번은 양수여야 합니다.");
        }
        if (refPayoutId == null || refPayoutId.isBlank()) {
            throw new IllegalArgumentException("지급대행 참조 식별자는 필수입니다.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등키는 필수입니다.");
        }
        Objects.requireNonNull(amount, "지급 금액은 필수입니다.");
        Objects.requireNonNull(status, "지급 시도 상태는 필수입니다.");
        Objects.requireNonNull(requestedAt, "지급 요청 시각은 필수입니다.");
    }
}
