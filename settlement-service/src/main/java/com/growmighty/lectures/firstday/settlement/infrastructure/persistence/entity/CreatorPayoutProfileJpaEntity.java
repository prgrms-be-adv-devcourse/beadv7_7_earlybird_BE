package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
public class CreatorPayoutProfileJpaEntity extends BaseEntity {

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

    protected CreatorPayoutProfileJpaEntity() {
    }

    private CreatorPayoutProfileJpaEntity(CreatorPayoutProfile profile) {
        this.creatorId = profile.creatorId();
        sync(profile);
    }

    public static CreatorPayoutProfileJpaEntity fromDomain(CreatorPayoutProfile profile) {
        if (profile.version() != null) {
            throw new IllegalArgumentException("이미 저장된 창작자 지급 프로필은 새 JPA 엔티티로 만들 수 없습니다.");
        }
        return new CreatorPayoutProfileJpaEntity(profile);
    }

    public void sync(CreatorPayoutProfile profile) {
        if (!Objects.equals(creatorId, profile.creatorId())) {
            throw new IllegalArgumentException("창작자 지급 프로필 식별자는 변경할 수 없습니다.");
        }
        this.tossSellerId = profile.tossSellerId();
        this.status = profile.status();
        this.bankCode = profile.bankCode();
        this.maskedAccountNumber = profile.maskedAccountNumber();
        this.verifiedAt = profile.verifiedAt();
    }

    public CreatorPayoutProfile toDomain() {
        return CreatorPayoutProfile.restore(
                creatorId,
                tossSellerId,
                status,
                bankCode,
                maskedAccountNumber,
                verifiedAt,
                version
        );
    }

    public Long creatorId() {
        return creatorId;
    }

    public Long version() {
        return version;
    }
}
