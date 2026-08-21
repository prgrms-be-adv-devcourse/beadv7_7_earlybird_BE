package com.growmighty.lectures.firstday.settlement.domain.model;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(
        name = "payout_obligations",
        uniqueConstraints = @UniqueConstraint(name = "uk_payout_obligation_settlement_id", columnNames = "settlement_id")
)
public class PayoutObligation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false, updatable = false, unique = true)
    private ProjectSettlement settlement;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private Money payoutAmount;

    @Column(name = "destination_toss_seller_id", nullable = false, updatable = false, length = 100)
    private String tossSellerId;

    @Column(name = "scheduled_date", nullable = false, updatable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PayoutStatus status;

    @OneToMany(mappedBy = "payoutObligation", cascade = CascadeType.ALL)
    @OrderBy("sequence ASC")
    private List<PayoutAttempt> attempts = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "successful_attempt_id", unique = true)
    private PayoutAttempt successfulAttempt;

    protected PayoutObligation() {
    }

    private PayoutObligation(
            ProjectSettlement settlement,
            CreatorPayoutProfile payoutProfile,
            LocalDate scheduledDate
    ) {
        this.settlement = Objects.requireNonNull(settlement, "프로젝트 정산은 필수입니다.");
        CreatorPayoutProfile profile = Objects.requireNonNull(payoutProfile, "창작자 지급 프로필은 필수입니다.");
        this.creatorId = profile.creatorId();
        this.payoutAmount = settlement.creatorPayoutAmount();
        this.tossSellerId = profile.tossSellerId();
        this.scheduledDate = scheduledDate;
        this.status = PayoutStatus.SCHEDULED;
        validateState();
    }

    public static PayoutObligation schedule(
            ProjectSettlement settlement,
            CreatorPayoutProfile payoutProfile,
            LocalDate scheduledDate
    ) {
        if (!payoutProfile.canReceivePayout() || !Objects.equals(settlement.creatorId(), payoutProfile.creatorId())) {
            throw new IllegalArgumentException("프로젝트 창작자의 지급 가능한 프로필이 필요합니다.");
        }
        return new PayoutObligation(settlement, payoutProfile, scheduledDate);
    }

    public Long id() { return id; }
    public Long settlementId() { return settlement.id(); }
    public Long creatorId() { return creatorId; }
    public Money payoutAmount() { return payoutAmount; }
    public String tossSellerId() { return tossSellerId; }
    public LocalDate scheduledDate() { return scheduledDate; }
    public PayoutStatus status() { return status; }
    public List<PayoutAttempt> attempts() { return List.copyOf(attempts); }
    public int attemptCount() { return attempts.size(); }
    public Optional<PayoutAttempt> latestAttempt() { return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.getLast()); }
    public Optional<PayoutAttempt> successfulAttempt() { return Optional.ofNullable(successfulAttempt); }

    public PayoutAttempt startAttempt(String refPayoutId, String idempotencyKey, LocalDateTime requestedAt) {
        if (status != PayoutStatus.SCHEDULED && status != PayoutStatus.RETRY_WAITING) {
            throw new IllegalStateException("현재 상태에서는 지급 시도를 시작할 수 없습니다: " + status);
        }
        PayoutAttempt attempt = PayoutAttempt.requested(this, attempts.size() + 1, refPayoutId, idempotencyKey, payoutAmount, requestedAt);
        attempts.add(attempt);
        status = PayoutStatus.PROCESSING;
        return attempt;
    }

    public void failAttempt(PayoutAttempt attempt, String tossPayoutId, String errorCode, LocalDateTime completedAt, boolean retryable) {
        requireProcessingAttempt(attempt);
        attempt.fail(tossPayoutId, errorCode, completedAt);
        status = retryable ? PayoutStatus.RETRY_WAITING : PayoutStatus.ACTION_REQUIRED;
    }

    public void acknowledgeAttempt(PayoutAttempt attempt, String tossPayoutId, PayoutAttemptStatus acknowledgedStatus) {
        requireProcessingAttempt(attempt);
        attempt.acknowledge(tossPayoutId, acknowledgedStatus);
    }

    public void completeAttempt(PayoutAttempt attempt, String tossPayoutId, LocalDateTime completedAt) {
        requireProcessingAttempt(attempt);
        if (successfulAttempt != null) throw new IllegalStateException("이미 성공한 지급 시도가 존재합니다.");
        attempt.complete(tossPayoutId, completedAt);
        successfulAttempt = attempt;
        status = PayoutStatus.COMPLETED;
    }

    public void markAttemptUnknown(PayoutAttempt attempt) {
        requireProcessingAttempt(attempt);
        attempt.markUnknown(null);
    }

    public void cancelAttempt(PayoutAttempt attempt, String tossPayoutId, LocalDateTime completedAt) {
        requireProcessingAttempt(attempt);
        attempt.cancel(tossPayoutId, completedAt);
        status = PayoutStatus.ACTION_REQUIRED;
    }

    @PostLoad
    private void validateState() {
        Objects.requireNonNull(settlement, "프로젝트 정산은 필수입니다.");
        if (!Objects.equals(creatorId, settlement.creatorId()) || !Objects.equals(payoutAmount, settlement.creatorPayoutAmount())) {
            throw new IllegalArgumentException("지급 의무는 프로젝트 정산의 창작자와 지급 금액을 사용해야 합니다.");
        }
        requireText(tossSellerId, "토스 셀러 식별자");
        Objects.requireNonNull(scheduledDate, "지급 예정일은 필수입니다.");
        Objects.requireNonNull(status, "지급 상태는 필수입니다.");
        Set<Integer> sequences = new HashSet<>();
        for (PayoutAttempt attempt : attempts) {
            if (!payoutAmount.equals(attempt.amount()) || !sequences.add(attempt.sequence())) {
                throw new IllegalArgumentException("지급 시도는 지급 의무의 고유 순번과 금액을 사용해야 합니다.");
            }
        }
        long completedAttempts = attempts.stream().filter(attempt -> attempt.status() == PayoutAttemptStatus.COMPLETED).count();
        if (completedAttempts > 1 || (status == PayoutStatus.COMPLETED) != (successfulAttempt != null)
                || (successfulAttempt != null && (!attempts.contains(successfulAttempt)
                || successfulAttempt.status() != PayoutAttemptStatus.COMPLETED))) {
            throw new IllegalArgumentException("지급 완료 상태와 성공한 지급 시도가 일치해야 합니다.");
        }
    }

    private void requireProcessingAttempt(PayoutAttempt attempt) {
        if (status != PayoutStatus.PROCESSING || !attempts.contains(attempt)) {
            throw new IllegalStateException("현재 지급 의무에 처리 중인 지급 시도가 아닙니다.");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "은 필수입니다.");
    }
}
