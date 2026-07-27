package com.growmighty.lectures.firstday.settlement.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "creator_payout_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_creator_payout_profile_toss_seller_id", columnNames = "toss_seller_id")
)
public class CreatorPayoutProfile extends BaseEntity {

    @Id
    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Column(name = "toss_seller_id", length = 100)
    private String tossSellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CreatorPayoutStatus status;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "masked_account_number", length = 100)
    private String maskedAccountNumber;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Version
    private Long version;

    protected CreatorPayoutProfile() {
    }

    private CreatorPayoutProfile(Long creatorId) {
        validateCreatorId(creatorId);
        this.creatorId = creatorId;
        this.status = CreatorPayoutStatus.REGISTRATION_PENDING;
    }

    private CreatorPayoutProfile(
            Long creatorId,
            String tossSellerId,
            CreatorPayoutStatus status,
            String bankCode,
            String maskedAccountNumber,
            LocalDateTime verifiedAt
    ) {
        validateCreatorId(creatorId);
        if (tossSellerId == null || tossSellerId.isBlank()) {
            throw new IllegalArgumentException("토스 셀러 식별자는 필수입니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("창작자 지급 상태는 필수입니다.");
        }
        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalArgumentException("은행 식별 정보는 필수입니다.");
        }
        if (maskedAccountNumber == null || maskedAccountNumber.isBlank()) {
            throw new IllegalArgumentException("마스킹된 계좌번호는 필수입니다.");
        }
        if (verifiedAt == null) {
            throw new IllegalArgumentException("계좌 검증 시각은 필수입니다.");
        }
        this.creatorId = creatorId;
        this.tossSellerId = tossSellerId;
        this.status = status;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
        this.verifiedAt = verifiedAt;
    }

    public static CreatorPayoutProfile registered(
            Long creatorId,
            String tossSellerId,
            CreatorPayoutStatus status,
            String bankCode,
            String maskedAccountNumber,
            LocalDateTime verifiedAt
    ) {
        return new CreatorPayoutProfile(
                creatorId,
                tossSellerId,
                status,
                bankCode,
                maskedAccountNumber,
                verifiedAt
        );
    }

    public static CreatorPayoutProfile awaitingRegistration(Long creatorId) {
        return new CreatorPayoutProfile(creatorId);
    }

    public void completeRegistration(
            String tossSellerId,
            CreatorPayoutStatus status,
            String bankCode,
            String maskedAccountNumber,
            LocalDateTime verifiedAt
    ) {
        if (this.status != CreatorPayoutStatus.REGISTRATION_PENDING) {
            throw new IllegalStateException("셀러 등록 대기 상태에서만 등록 결과를 반영할 수 있습니다.");
        }
        if (tossSellerId == null || tossSellerId.isBlank()) {
            throw new IllegalArgumentException("토스 셀러 식별자는 필수입니다.");
        }
        if (status == null || status == CreatorPayoutStatus.REGISTRATION_PENDING) {
            throw new IllegalArgumentException("등록 완료 후의 창작자 지급 상태가 필요합니다.");
        }
        if (bankCode == null || bankCode.isBlank()) {
            throw new IllegalArgumentException("은행 식별 정보는 필수입니다.");
        }
        if (maskedAccountNumber == null || maskedAccountNumber.isBlank()) {
            throw new IllegalArgumentException("마스킹된 계좌번호는 필수입니다.");
        }
        if (verifiedAt == null) {
            throw new IllegalArgumentException("계좌 검증 시각은 필수입니다.");
        }
        this.tossSellerId = tossSellerId;
        this.status = status;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
        this.verifiedAt = verifiedAt;
    }

    public boolean canReceivePayout() {
        return status == CreatorPayoutStatus.PAYOUT_READY;
    }

    public PayoutDestinationSnapshot snapshotDestination() {
        if (!canReceivePayout()) {
            throw new IllegalStateException("지급 가능한 창작자만 지급 대상 스냅샷을 만들 수 있습니다.");
        }
        return PayoutDestinationSnapshot.of(creatorId, tossSellerId, bankCode, maskedAccountNumber);
    }

    private static void validateCreatorId(Long creatorId) {
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
    }
}
