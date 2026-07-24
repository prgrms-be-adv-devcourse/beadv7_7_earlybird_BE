package com.growmighty.lectures.firstday.settlement.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class PayoutAttempt {

    private final Long id;
    private final int sequence;
    private final String refPayoutId;
    private String tossPayoutId;
    private final String idempotencyKey;
    private final Money amount;
    private PayoutAttemptStatus status;
    private String errorCode;
    private final LocalDateTime requestedAt;
    private LocalDateTime completedAt;

    private PayoutAttempt(
            Long id,
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
        this.id = id;
        this.sequence = sequence;
        this.refPayoutId = refPayoutId;
        this.tossPayoutId = tossPayoutId;
        this.idempotencyKey = idempotencyKey;
        this.amount = Objects.requireNonNull(amount, "지급 금액은 필수입니다.");
        this.status = Objects.requireNonNull(status, "지급 시도 상태는 필수입니다.");
        this.errorCode = errorCode;
        this.requestedAt = Objects.requireNonNull(requestedAt, "지급 요청 시각은 필수입니다.");
        this.completedAt = completedAt;
    }

    static PayoutAttempt requested(
            int sequence,
            String refPayoutId,
            String idempotencyKey,
            Money amount,
            LocalDateTime requestedAt
    ) {
        return new PayoutAttempt(
                null,
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

    public static PayoutAttempt restore(
            Long id,
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
        return new PayoutAttempt(
                Objects.requireNonNull(id, "지급 시도 식별자는 필수입니다."),
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
