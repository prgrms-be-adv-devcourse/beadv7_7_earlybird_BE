package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.settlement.domain.PayoutDestinationSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class PayoutDestinationSnapshotJpaEmbeddable {

    @Column(name = "destination_creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Column(name = "destination_toss_seller_id", nullable = false, updatable = false, length = 100)
    private String tossSellerId;

    @Column(name = "destination_bank_code", nullable = false, updatable = false, length = 20)
    private String bankCode;

    @Column(name = "destination_masked_account_number", nullable = false, updatable = false, length = 100)
    private String maskedAccountNumber;

    protected PayoutDestinationSnapshotJpaEmbeddable() {
    }

    private PayoutDestinationSnapshotJpaEmbeddable(PayoutDestinationSnapshot snapshot) {
        this.creatorId = snapshot.creatorId();
        this.tossSellerId = snapshot.tossSellerId();
        this.bankCode = snapshot.bankCode();
        this.maskedAccountNumber = snapshot.maskedAccountNumber();
    }

    static PayoutDestinationSnapshotJpaEmbeddable fromDomain(PayoutDestinationSnapshot snapshot) {
        return new PayoutDestinationSnapshotJpaEmbeddable(snapshot);
    }

    PayoutDestinationSnapshot toDomain() {
        return PayoutDestinationSnapshot.of(creatorId, tossSellerId, bankCode, maskedAccountNumber);
    }
}
