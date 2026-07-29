package com.growmighty.lectures.firstday.settlement.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PayoutObligation {

    private final Long id;
    private final Long settlementId;
    private final Long creatorId;
    private final Money amount;
    private final LocalDate scheduledDate;
    private PayoutObligationStatus status;
    private final List<PayoutAttempt> attempts;
    private PayoutAttempt successfulAttempt;
    private final Long version;

    private PayoutObligation(
            Long id,
            Long settlementId,
            Long creatorId,
            Money amount,
            LocalDate scheduledDate,
            PayoutObligationStatus status,
            List<PayoutAttempt> attempts,
            Integer successfulAttemptSequence,
            Long version
    ) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("지급 의무 식별자는 양수여야 합니다.");
        }
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException("프로젝트 정산 식별자는 양수여야 합니다.");
        }
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        this.id = id;
        this.settlementId = settlementId;
        this.creatorId = creatorId;
        this.amount = Objects.requireNonNull(amount, "지급 금액은 필수입니다.");
        this.scheduledDate = Objects.requireNonNull(scheduledDate, "지급 예정일은 필수입니다.");
        this.status = Objects.requireNonNull(status, "지급 의무 상태는 필수입니다.");
        this.attempts = new ArrayList<>(Objects.requireNonNull(attempts, "지급 시도 목록은 필수입니다."));
        this.version = version;

        if (successfulAttemptSequence != null) {
            this.successfulAttempt = this.attempts.stream()
                    .filter(attempt -> attempt.sequence() == successfulAttemptSequence)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("성공한 지급 시도가 지급 시도 목록에 없습니다."));
        }
    }

    public static PayoutObligation schedule(
            Long settlementId,
            Long creatorId,
            Money amount,
            LocalDate scheduledDate
    ) {
        return new PayoutObligation(
                null,
                settlementId,
                creatorId,
                amount,
                scheduledDate,
                PayoutObligationStatus.SCHEDULED,
                List.of(),
                null,
                null
        );
    }

    public static PayoutObligation restore(
            Long id,
            Long settlementId,
            Long creatorId,
            Money amount,
            LocalDate scheduledDate,
            PayoutObligationStatus status,
            List<PayoutAttempt> attempts,
            Integer successfulAttemptSequence,
            Long version
    ) {
        return new PayoutObligation(
                Objects.requireNonNull(id, "지급 의무 식별자는 필수입니다."),
                settlementId,
                creatorId,
                amount,
                scheduledDate,
                status,
                attempts,
                successfulAttemptSequence,
                version
        );
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

    public void acknowledgeAttempt(
            PayoutAttempt attempt,
            String tossPayoutId,
            PayoutAttemptStatus acknowledgedStatus
    ) {
        requireProcessingAttempt(attempt);
        attempt.acknowledge(tossPayoutId, acknowledgedStatus);
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
        markAttemptUnknown(attempt, null);
    }

    public void markAttemptUnknown(PayoutAttempt attempt, String errorCode) {
        requireProcessingAttempt(attempt);
        attempt.markUnknown(errorCode);
    }

    public void cancelAttempt(
            PayoutAttempt attempt,
            String tossPayoutId,
            LocalDateTime completedAt
    ) {
        requireProcessingAttempt(attempt);
        attempt.cancel(tossPayoutId, completedAt);
        status = PayoutObligationStatus.ACTION_REQUIRED;
    }

    public Long id() {
        return id;
    }

    public Long settlementId() {
        return settlementId;
    }

    public Long creatorId() {
        return creatorId;
    }

    public Money amount() {
        return amount;
    }

    public LocalDate scheduledDate() {
        return scheduledDate;
    }

    public PayoutObligationStatus status() {
        return status;
    }

    public List<PayoutAttempt> attempts() {
        return List.copyOf(attempts);
    }

    public Integer successfulAttemptSequence() {
        return successfulAttempt == null ? null : successfulAttempt.sequence();
    }

    public Long version() {
        return version;
    }

    public int attemptCount() {
        return attempts.size();
    }

    public Optional<PayoutAttempt> latestAttempt() {
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(attempts.getLast());
    }

    public Optional<PayoutAttempt> successfulAttempt() {
        return Optional.ofNullable(successfulAttempt);
    }

    public boolean isCompleted() {
        return status == PayoutObligationStatus.COMPLETED;
    }

    private void requireProcessingAttempt(PayoutAttempt attempt) {
        if (status != PayoutObligationStatus.PROCESSING || !attempts.contains(attempt)) {
            throw new IllegalStateException("현재 지급 의무에 처리 중인 지급 시도가 아닙니다.");
        }
    }
}
