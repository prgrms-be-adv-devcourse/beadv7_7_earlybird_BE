// TODO(settlement-plan): Keep the confirmed financial record immutable and create it only from reconciled successful-project payments.
package com.growmighty.lectures.firstday.settlement.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class ProjectSettlement {

    private final Long id;
    private final Long projectId;
    private final Long creatorId;
    private final SettlementFeePolicySnapshot feePolicySnapshot;
    private final SettlementBreakdown breakdown;
    private final PayoutDestinationSnapshot destinationSnapshot;
    private final LocalDate scheduledDate;
    private final PayoutStatus status;
    private final LocalDateTime confirmedAt;

    private ProjectSettlement(
            Long id,
            Long projectId,
            Long creatorId,
            SettlementFeePolicySnapshot feePolicySnapshot,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDate scheduledDate,
            PayoutStatus status,
            LocalDateTime confirmedAt
    ) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("프로젝트 정산 식별자는 양수여야 합니다.");
        }
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        this.id = id;
        this.projectId = projectId;
        this.creatorId = creatorId;
        this.feePolicySnapshot = Objects.requireNonNull(feePolicySnapshot, "수수료 정책 스냅샷은 필수입니다.");
        this.breakdown = Objects.requireNonNull(breakdown, "정산 금액 명세는 필수입니다.");
        this.destinationSnapshot = Objects.requireNonNull(destinationSnapshot, "지급 대상 스냅샷은 필수입니다.");
        if (!destinationSnapshot.belongsTo(creatorId)) {
            throw new IllegalArgumentException("프로젝트 창작자와 지급 대상 창작자가 일치해야 합니다.");
        }
        this.scheduledDate = Objects.requireNonNull(scheduledDate, "지급 예정일은 필수입니다.");
        this.status = Objects.requireNonNull(status, "지급 상태는 필수입니다.");
        this.confirmedAt = Objects.requireNonNull(confirmedAt, "정산 확정 시각은 필수입니다.");
    }

    public static ProjectSettlement confirm(
            Long projectId,
            Long creatorId,
            List<Money> orderPaymentAmounts,
            CreatorPayoutProfile payoutProfile,
            LocalDate scheduledDate,
            LocalDateTime confirmedAt
    ) {
        List<Money> amounts = List.copyOf(Objects.requireNonNull(
                orderPaymentAmounts,
                "주문 결제금액 목록은 필수입니다."
        ));
        CreatorPayoutProfile profile = Objects.requireNonNull(payoutProfile, "창작자 지급 프로필은 필수입니다.");
        if (!profile.creatorId().equals(creatorId) || !profile.canReceivePayout()) {
            throw new IllegalArgumentException("프로젝트 창작자의 지급 가능한 프로필이 필요합니다.");
        }
        SettlementCalculationPolicy calculationPolicy = SettlementCalculationPolicy.current();
        return new ProjectSettlement(
                null,
                projectId,
                creatorId,
                calculationPolicy.feePolicySnapshot(),
                calculationPolicy.calculate(amounts),
                profile.snapshotDestination(),
                scheduledDate,
                PayoutStatus.SCHEDULED,
                confirmedAt
        );
    }

    public static ProjectSettlement restore(
            Long id,
            Long projectId,
            Long creatorId,
            SettlementFeePolicySnapshot feePolicySnapshot,
            SettlementBreakdown breakdown,
            PayoutDestinationSnapshot destinationSnapshot,
            LocalDate scheduledDate,
            PayoutStatus status,
            LocalDateTime confirmedAt
    ) {
        return new ProjectSettlement(
                Objects.requireNonNull(id, "프로젝트 정산 식별자는 필수입니다."),
                projectId,
                creatorId,
                feePolicySnapshot,
                breakdown,
                destinationSnapshot,
                scheduledDate,
                status,
                confirmedAt
        );
    }

    public Long id() {
        return id;
    }

    public Long projectId() {
        return projectId;
    }

    public Long creatorId() {
        return creatorId;
    }

    public SettlementFeePolicySnapshot feePolicySnapshot() {
        return feePolicySnapshot;
    }

    public SettlementBreakdown breakdown() {
        return breakdown;
    }

    public BigDecimal paymentAndSettlementAgencyFeeRate() {
        return feePolicySnapshot.paymentAndSettlementAgencyFeeRate();
    }

    public BigDecimal platformFeeRate() {
        return feePolicySnapshot.platformFeeRate();
    }

    public BigDecimal vatRate() {
        return feePolicySnapshot.vatRate();
    }

    public Money baseAmount() {
        return breakdown.baseAmount();
    }

    public Money paymentAndSettlementAgencyFeeAmount() {
        return breakdown.paymentAndSettlementAgencyFeeAmount();
    }

    public Money paymentAndSettlementAgencyFeeVatAmount() {
        return breakdown.paymentAndSettlementAgencyFeeVatAmount();
    }

    public Money platformFeeAmount() {
        return breakdown.platformFeeAmount();
    }

    public Money platformFeeVatAmount() {
        return breakdown.platformFeeVatAmount();
    }

    public Money otherDeductionAmount() {
        return breakdown.otherDeductionAmount();
    }

    public Money creatorPayoutAmount() {
        return breakdown.creatorPayoutAmount();
    }

    public String tossSellerId() {
        return destinationSnapshot.tossSellerId();
    }

    public String bankCode() {
        return destinationSnapshot.bankCode();
    }

    public String maskedAccountNumber() {
        return destinationSnapshot.maskedAccountNumber();
    }

    public PayoutDestinationSnapshot destinationSnapshot() {
        return destinationSnapshot;
    }

    public LocalDate scheduledDate() {
        return scheduledDate;
    }

    public PayoutStatus status() {
        return status;
    }

    public LocalDateTime confirmedAt() {
        return confirmedAt;
    }
}
