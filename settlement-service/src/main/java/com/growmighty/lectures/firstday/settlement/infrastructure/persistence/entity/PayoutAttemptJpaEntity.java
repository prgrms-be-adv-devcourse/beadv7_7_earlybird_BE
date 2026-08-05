package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.converter.MoneyAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
public class PayoutAttemptJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_obligation_id", nullable = false, updatable = false)
    private PayoutObligationJpaEntity payoutObligation;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Column(name = "ref_payout_id", nullable = false, updatable = false, length = 50)
    private String refPayoutId;

    @Column(name = "toss_payout_id", unique = true, length = 35)
    private String tossPayoutId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 300)
    private String idempotencyKey;

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 0)
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

    protected PayoutAttemptJpaEntity() {
    }

    private PayoutAttemptJpaEntity(PayoutAttempt attempt, PayoutObligationJpaEntity payoutObligation) {
        this.payoutObligation = Objects.requireNonNull(payoutObligation, "지급 의무 JPA 엔티티는 필수입니다.");
        this.sequence = attempt.sequence();
        this.refPayoutId = attempt.refPayoutId();
        this.idempotencyKey = attempt.idempotencyKey();
        this.amount = attempt.amount();
        sync(attempt);
    }

    static PayoutAttemptJpaEntity fromDomain(
            PayoutAttempt attempt,
            PayoutObligationJpaEntity payoutObligation
    ) {
        if (attempt.id() != null) {
            throw new IllegalArgumentException("이미 저장된 지급 시도는 새 JPA 엔티티로 만들 수 없습니다.");
        }
        return new PayoutAttemptJpaEntity(attempt, payoutObligation);
    }

    void sync(PayoutAttempt attempt) {
        if (id != null && !Objects.equals(id, attempt.id())) {
            throw new IllegalArgumentException("지급 시도 식별자는 변경할 수 없습니다.");
        }
        if (sequence != attempt.sequence()
                || !Objects.equals(refPayoutId, attempt.refPayoutId())
                || !Objects.equals(idempotencyKey, attempt.idempotencyKey())
                || !Objects.equals(amount, attempt.amount())) {
            throw new IllegalArgumentException("지급 시도의 확정 정보는 변경할 수 없습니다.");
        }
        this.tossPayoutId = attempt.tossPayoutId();
        this.status = attempt.status();
        this.errorCode = attempt.errorCode();
        this.requestedAt = attempt.requestedAt();
        this.completedAt = attempt.completedAt();
    }

    PayoutAttempt toDomain() {
        return PayoutAttempt.restore(
                id,
                sequence,
                refPayoutId,
                tossPayoutId,
                idempotencyKey,
                amount,
                status,
                errorCode,
                requestedAt,
                completedAt
        );
    }

    Long id() {
        return id;
    }

    int sequence() {
        return sequence;
    }
}
