package com.growmighty.lectures.firstday.settlement.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "payout_obligations",
        uniqueConstraints = @UniqueConstraint(name = "uk_payout_obligation_settlement_id", columnNames = "settlement_id")
)
public class PayoutObligation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, updatable = false)
    private Long settlementId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "payout_amount", nullable = false, precision = 19, scale = 0))
    private Money amount;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PayoutObligationStatus status;

    @OneToMany(mappedBy = "payoutObligation", cascade = CascadeType.ALL)
    private List<PayoutAttempt> attempts = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "successful_attempt_id", unique = true)
    private PayoutAttempt successfulAttempt;

    @Version
    private Long version;

    protected PayoutObligation() {
    }

    private PayoutObligation(Long settlementId, Long creatorId, Money amount, LocalDate scheduledDate) {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException("프로젝트 정산 식별자는 양수여야 합니다.");
        }
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        this.settlementId = settlementId;
        this.creatorId = creatorId;
        this.amount = Objects.requireNonNull(amount, "지급 금액은 필수입니다.");
        this.scheduledDate = Objects.requireNonNull(scheduledDate, "지급 예정일은 필수입니다.");
        this.status = PayoutObligationStatus.SCHEDULED;
    }

    public static PayoutObligation schedule(
            Long settlementId,
            Long creatorId,
            Money amount,
            LocalDate scheduledDate
    ) {
        return new PayoutObligation(settlementId, creatorId, amount, scheduledDate);
    }

    public PayoutAttempt startAttempt(
            String refPayoutId,
            String idempotencyKey,
            LocalDateTime requestedAt
    ) {
        if (status != PayoutObligationStatus.SCHEDULED && status != PayoutObligationStatus.RETRY_WAITING) {
            throw new IllegalStateException("현재 상태에서는 지급 시도를 시작할 수 없습니다: " + status);
        }
        PayoutAttempt attempt = PayoutAttempt.requested(
                this,
                attempts.size() + 1,
                refPayoutId,
                idempotencyKey,
                amount,
                requestedAt
        );
        attempts.add(attempt);
        status = PayoutObligationStatus.PROCESSING;
        return attempt;
    }

    public void failAttempt(
            PayoutAttempt attempt,
            String tossPayoutId,
            String errorCode,
            LocalDateTime completedAt,
            boolean retryable
    ) {
        if (status != PayoutObligationStatus.PROCESSING || !attempts.contains(attempt)) {
            throw new IllegalStateException("현재 지급 의무에 처리 중인 지급 시도가 아닙니다.");
        }
        attempt.fail(tossPayoutId, errorCode, completedAt);
        status = retryable ? PayoutObligationStatus.RETRY_WAITING : PayoutObligationStatus.ACTION_REQUIRED;
    }

    public void completeAttempt(
            PayoutAttempt attempt,
            String tossPayoutId,
            LocalDateTime completedAt
    ) {
        if (status != PayoutObligationStatus.PROCESSING || !attempts.contains(attempt)) {
            throw new IllegalStateException("현재 지급 의무에 처리 중인 지급 시도가 아닙니다.");
        }
        if (successfulAttempt != null) {
            throw new IllegalStateException("이미 성공한 지급 시도가 존재합니다.");
        }
        attempt.complete(tossPayoutId, completedAt);
        successfulAttempt = attempt;
        status = PayoutObligationStatus.COMPLETED;
    }

    public void markAttemptUnknown(PayoutAttempt attempt) {
        if (status != PayoutObligationStatus.PROCESSING || !attempts.contains(attempt)) {
            throw new IllegalStateException("현재 지급 의무에 처리 중인 지급 시도가 아닙니다.");
        }
        attempt.markUnknown();
    }

    public int attemptCount() {
        return attempts.size();
    }

    public boolean isCompleted() {
        return status == PayoutObligationStatus.COMPLETED;
    }
}
