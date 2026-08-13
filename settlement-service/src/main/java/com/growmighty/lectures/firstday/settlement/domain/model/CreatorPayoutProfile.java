package com.growmighty.lectures.firstday.settlement.domain.model;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;

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

    private CreatorPayoutProfile(
            Long creatorId,
            String tossSellerId,
            CreatorPayoutStatus status,
            String bankCode,
            String maskedAccountNumber,
            LocalDateTime verifiedAt
    ) {
        this.creatorId = creatorId;
        this.tossSellerId = tossSellerId;
        this.status = status;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
        this.verifiedAt = verifiedAt;
        validateState();
    }

    public static CreatorPayoutProfile registered(
            Long creatorId,
            String tossSellerId,
            CreatorPayoutStatus status,
            String bankCode,
            String maskedAccountNumber,
            LocalDateTime verifiedAt
    ) {
        if (status == CreatorPayoutStatus.REGISTRATION_PENDING) {
            throw new IllegalArgumentException("등록 완료 후의 창작자 지급 상태가 필요합니다.");
        }
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
        return new CreatorPayoutProfile(
                creatorId,
                null,
                CreatorPayoutStatus.REGISTRATION_PENDING,
                null,
                null,
                null
        );
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
        if (status == null || status == CreatorPayoutStatus.REGISTRATION_PENDING) {
            throw new IllegalArgumentException("등록 완료 후의 창작자 지급 상태가 필요합니다.");
        }
        validateRegistrationDetails(tossSellerId, bankCode, maskedAccountNumber, verifiedAt);
        this.tossSellerId = tossSellerId;
        this.status = status;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
        this.verifiedAt = verifiedAt;
    }

    public Long creatorId() {
        return creatorId;
    }

    public String tossSellerId() {
        return tossSellerId;
    }

    public CreatorPayoutStatus status() {
        return status;
    }

    public String bankCode() {
        return bankCode;
    }

    public String maskedAccountNumber() {
        return maskedAccountNumber;
    }

    public LocalDateTime verifiedAt() {
        return verifiedAt;
    }

    public Long version() {
        return version;
    }

    public boolean canReceivePayout() {
        return status == CreatorPayoutStatus.PAYOUT_READY;
    }

    private static void validateCreatorId(Long creatorId) {
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
    }

    @PostLoad
    private void validateState() {
        validateCreatorId(creatorId);
        Objects.requireNonNull(status, "창작자 지급 상태는 필수입니다.");
        if (status == CreatorPayoutStatus.REGISTRATION_PENDING) {
            if (tossSellerId != null || bankCode != null || maskedAccountNumber != null || verifiedAt != null) {
                throw new IllegalArgumentException("셀러 등록 대기 중에는 외부 셀러와 계좌 정보를 가질 수 없습니다.");
            }
            return;
        }
        validateRegistrationDetails(tossSellerId, bankCode, maskedAccountNumber, verifiedAt);
    }

    private static void validateRegistrationDetails(
            String tossSellerId,
            String bankCode,
            String maskedAccountNumber,
            LocalDateTime verifiedAt
    ) {
        if (tossSellerId == null || tossSellerId.isBlank()) {
            throw new IllegalArgumentException("토스 셀러 식별자는 필수입니다.");
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
    }
}
