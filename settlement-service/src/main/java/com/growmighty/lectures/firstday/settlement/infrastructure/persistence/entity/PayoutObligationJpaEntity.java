// TODO(settlement-plan): Enforce one obligation per settlement and one successful attempt through mappings and constraints.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.converter.MoneyAttributeConverter;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
        name = "payout_obligations",
        uniqueConstraints = @UniqueConstraint(name = "uk_payout_obligation_settlement_id", columnNames = "settlement_id")
)
public class PayoutObligationJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, updatable = false)
    private Long settlementId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Convert(converter = MoneyAttributeConverter.class)
    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 0)
    private Money amount;

    @Column(name = "scheduled_date", nullable = false, updatable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PayoutObligationStatus status;

    @OneToMany(mappedBy = "payoutObligation", cascade = CascadeType.ALL)
    @OrderBy("sequence ASC")
    private List<PayoutAttemptJpaEntity> attempts = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "successful_attempt_id", unique = true)
    private PayoutAttemptJpaEntity successfulAttempt;

    @Version
    private Long version;

    protected PayoutObligationJpaEntity() {
    }

    private PayoutObligationJpaEntity(PayoutObligation obligation) {
        this.settlementId = obligation.settlementId();
        this.creatorId = obligation.creatorId();
        this.amount = obligation.amount();
        this.scheduledDate = obligation.scheduledDate();
        this.status = obligation.status();
        obligation.attempts().forEach(attempt -> attempts.add(PayoutAttemptJpaEntity.fromDomain(attempt, this)));
        this.successfulAttempt = findAttemptBySequence(obligation.successfulAttemptSequence());
    }

    public static PayoutObligationJpaEntity fromDomain(PayoutObligation obligation) {
        if (obligation.id() != null || obligation.version() != null) {
            throw new IllegalArgumentException("이미 저장된 지급 의무는 새 JPA 엔티티로 만들 수 없습니다.");
        }
        return new PayoutObligationJpaEntity(obligation);
    }

    public void sync(PayoutObligation obligation) {
        if (!Objects.equals(id, obligation.id())) {
            throw new IllegalArgumentException("지급 의무 식별자는 변경할 수 없습니다.");
        }
        if (!Objects.equals(settlementId, obligation.settlementId())
                || !Objects.equals(creatorId, obligation.creatorId())
                || !Objects.equals(amount, obligation.amount())
                || !Objects.equals(scheduledDate, obligation.scheduledDate())) {
            throw new IllegalArgumentException("지급 의무의 확정 정보는 변경할 수 없습니다.");
        }

        Set<Long> domainAttemptIds = new HashSet<>();
        for (PayoutAttempt attempt : obligation.attempts()) {
            if (attempt.id() == null) {
                attempts.add(PayoutAttemptJpaEntity.fromDomain(attempt, this));
                continue;
            }
            domainAttemptIds.add(attempt.id());
            PayoutAttemptJpaEntity entity = attempts.stream()
                    .filter(candidate -> Objects.equals(candidate.id(), attempt.id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("저장된 지급 시도가 지급 의무에 존재하지 않습니다."));
            entity.sync(attempt);
        }

        boolean existingAttemptMissing = attempts.stream()
                .filter(attempt -> attempt.id() != null)
                .anyMatch(attempt -> !domainAttemptIds.contains(attempt.id()));
        if (existingAttemptMissing) {
            throw new IllegalArgumentException("지급 시도 기록은 제거할 수 없습니다.");
        }

        this.status = obligation.status();
        this.successfulAttempt = findAttemptBySequence(obligation.successfulAttemptSequence());
    }

    public PayoutObligation toDomain() {
        List<PayoutAttempt> domainAttempts = attempts.stream()
                .map(PayoutAttemptJpaEntity::toDomain)
                .toList();
        return PayoutObligation.restore(
                id,
                settlementId,
                creatorId,
                amount,
                scheduledDate,
                status,
                domainAttempts,
                successfulAttempt == null ? null : successfulAttempt.sequence(),
                version
        );
    }

    public Long id() {
        return id;
    }

    public Long version() {
        return version;
    }

    private PayoutAttemptJpaEntity findAttemptBySequence(Integer sequence) {
        if (sequence == null) {
            return null;
        }
        return attempts.stream()
                .filter(attempt -> attempt.sequence() == sequence)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("성공한 지급 시도가 JPA 지급 시도 목록에 없습니다."));
    }
}
