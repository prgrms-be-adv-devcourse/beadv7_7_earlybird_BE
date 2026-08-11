// TODO(settlement-plan): Align obligation transitions with preparation, in-flight, completion, and operator-action states.
package com.growmighty.lectures.firstday.settlement.domain.model;

public enum PayoutObligationStatus {
    CREATOR_PAYOUT_PROFILE_WAITING,
    SCHEDULED,
    PROCESSING,
    RETRY_WAITING,
    COMPLETED,
    ACTION_REQUIRED;

    public static PayoutObligationStatus fromPayoutStatus(PayoutStatus status) {
        return switch (status) {
            case SCHEDULED -> SCHEDULED;
            case PROCESSING -> PROCESSING;
            case RETRY_WAITING -> RETRY_WAITING;
            case COMPLETED -> COMPLETED;
            case ACTION_REQUIRED -> ACTION_REQUIRED;
        };
    }

    public PayoutStatus toPayoutStatus() {
        return switch (this) {
            case CREATOR_PAYOUT_PROFILE_WAITING -> throw new IllegalStateException(
                    "등록 대기 중인 지급 의무는 프로젝트 정산 지급 상태로 복원할 수 없습니다."
            );
            case SCHEDULED -> PayoutStatus.SCHEDULED;
            case PROCESSING -> PayoutStatus.PROCESSING;
            case RETRY_WAITING -> PayoutStatus.RETRY_WAITING;
            case COMPLETED -> PayoutStatus.COMPLETED;
            case ACTION_REQUIRED -> PayoutStatus.ACTION_REQUIRED;
        };
    }
}
