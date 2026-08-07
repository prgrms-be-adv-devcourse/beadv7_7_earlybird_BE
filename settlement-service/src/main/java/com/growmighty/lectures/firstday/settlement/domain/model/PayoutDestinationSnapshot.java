// TODO(settlement-plan): Name the Toss seller destination explicitly and keep only masked, immutable payout target data.
package com.growmighty.lectures.firstday.settlement.domain.model;

import java.util.Objects;

public final class PayoutDestinationSnapshot {

    private final Long creatorId;
    private final String tossSellerId;
    private final String bankCode;
    private final String maskedAccountNumber;

    private PayoutDestinationSnapshot(
            Long creatorId,
            String tossSellerId,
            String bankCode,
            String maskedAccountNumber
    ) {
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        if (tossSellerId == null || tossSellerId.isBlank()) {
            throw new IllegalArgumentException("토스 셀러 식별자는 필수입니다.");
        }
        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalArgumentException("은행 식별 정보는 필수입니다.");
        }
        if (maskedAccountNumber == null || maskedAccountNumber.isBlank()) {
            throw new IllegalArgumentException("마스킹된 계좌번호는 필수입니다.");
        }
        this.creatorId = creatorId;
        this.tossSellerId = tossSellerId;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
    }

    public static PayoutDestinationSnapshot of(
            Long creatorId,
            String tossSellerId,
            String bankCode,
            String maskedAccountNumber
    ) {
        return new PayoutDestinationSnapshot(creatorId, tossSellerId, bankCode, maskedAccountNumber);
    }

    public Long creatorId() {
        return creatorId;
    }

    public String tossSellerId() {
        return tossSellerId;
    }

    public String bankCode() {
        return bankCode;
    }

    public String maskedAccountNumber() {
        return maskedAccountNumber;
    }

    public boolean belongsTo(Long creatorId) {
        return this.creatorId.equals(creatorId);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PayoutDestinationSnapshot other)) {
            return false;
        }
        return creatorId.equals(other.creatorId)
                && tossSellerId.equals(other.tossSellerId)
                && bankCode.equals(other.bankCode)
                && maskedAccountNumber.equals(other.maskedAccountNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(creatorId, tossSellerId, bankCode, maskedAccountNumber);
    }
}
