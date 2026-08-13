package com.growmighty.lectures.firstday.settlement.domain.model;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "project_settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_settlement_project_id", columnNames = "project_id")
)
public class ProjectSettlement extends BaseEntity {

    private static final BigDecimal PAYMENT_AND_SETTLEMENT_AGENCY_FEE_RATE = new BigDecimal("0.04");
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.04");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Column(name = "payment_and_settlement_agency_fee_rate", nullable = false, precision = 7, scale = 6, updatable = false)
    private BigDecimal paymentAndSettlementAgencyFeeRate;

    @Column(name = "platform_fee_rate", nullable = false, precision = 7, scale = 6, updatable = false)
    private BigDecimal platformFeeRate;

    @Column(name = "fee_vat_rate", nullable = false, precision = 7, scale = 6, updatable = false)
    private BigDecimal vatRate;

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private Money baseAmount;

    @Column(name = "agency_fee_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private Money paymentAndSettlementAgencyFeeAmount;

    @Column(name = "agency_fee_vat_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private Money paymentAndSettlementAgencyFeeVatAmount;

    @Column(name = "platform_fee_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private Money platformFeeAmount;

    @Column(name = "platform_fee_vat_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private Money platformFeeVatAmount;

    @Column(name = "other_deduction_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private Money otherDeductionAmount;

    @Column(name = "creator_payout_amount", nullable = false, precision = 19, scale = 0, updatable = false)
    private Money creatorPayoutAmount;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    protected ProjectSettlement() {
    }

    private ProjectSettlement(
            Long projectId,
            Long creatorId,
            Money baseAmount,
            Money paymentAndSettlementAgencyFeeAmount,
            Money paymentAndSettlementAgencyFeeVatAmount,
            Money platformFeeAmount,
            Money platformFeeVatAmount,
            Money otherDeductionAmount,
            Money creatorPayoutAmount,
            LocalDateTime confirmedAt
    ) {
        this.projectId = projectId;
        this.creatorId = creatorId;
        this.paymentAndSettlementAgencyFeeRate = PAYMENT_AND_SETTLEMENT_AGENCY_FEE_RATE.stripTrailingZeros();
        this.platformFeeRate = PLATFORM_FEE_RATE.stripTrailingZeros();
        this.vatRate = VAT_RATE.stripTrailingZeros();
        this.baseAmount = baseAmount;
        this.paymentAndSettlementAgencyFeeAmount = paymentAndSettlementAgencyFeeAmount;
        this.paymentAndSettlementAgencyFeeVatAmount = paymentAndSettlementAgencyFeeVatAmount;
        this.platformFeeAmount = platformFeeAmount;
        this.platformFeeVatAmount = platformFeeVatAmount;
        this.otherDeductionAmount = otherDeductionAmount;
        this.creatorPayoutAmount = creatorPayoutAmount;
        this.confirmedAt = confirmedAt;
        validateState();
    }

    public static ProjectSettlement confirm(
            Long projectId,
            Long creatorId,
            List<Money> orderPaymentAmounts,
            LocalDateTime confirmedAt
    ) {
        List<Money> amounts = List.copyOf(Objects.requireNonNull(orderPaymentAmounts, "주문 결제금액 목록은 필수입니다."));
        Money baseAmount = Money.wons(amounts.stream().map(Money::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (baseAmount.amount().signum() == 0) {
            throw new IllegalArgumentException("프로젝트 정산 기준 금액은 0원보다 커야 합니다.");
        }
        Money agencyFeeAmount = Money.wons(amounts.stream()
                .map(Money::amount)
                .map(amount -> applyRate(amount, PAYMENT_AND_SETTLEMENT_AGENCY_FEE_RATE))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        Money agencyFeeVatAmount = Money.wons(applyRate(agencyFeeAmount.amount(), VAT_RATE));
        Money platformFeeAmount = Money.wons(applyRate(baseAmount.amount(), PLATFORM_FEE_RATE));
        Money platformFeeVatAmount = Money.wons(applyRate(platformFeeAmount.amount(), VAT_RATE));
        Money otherDeductionAmount = Money.wons(0);
        Money creatorPayoutAmount = baseAmount.minus(agencyFeeAmount)
                .minus(agencyFeeVatAmount)
                .minus(platformFeeAmount)
                .minus(platformFeeVatAmount)
                .minus(otherDeductionAmount);
        return new ProjectSettlement(projectId, creatorId, baseAmount, agencyFeeAmount, agencyFeeVatAmount,
                platformFeeAmount, platformFeeVatAmount, otherDeductionAmount, creatorPayoutAmount, confirmedAt);
    }

    public Long id() { return id; }
    public Long projectId() { return projectId; }
    public Long creatorId() { return creatorId; }
    public BigDecimal paymentAndSettlementAgencyFeeRate() { return paymentAndSettlementAgencyFeeRate; }
    public BigDecimal platformFeeRate() { return platformFeeRate; }
    public BigDecimal vatRate() { return vatRate; }
    public Money baseAmount() { return baseAmount; }
    public Money paymentAndSettlementAgencyFeeAmount() { return paymentAndSettlementAgencyFeeAmount; }
    public Money paymentAndSettlementAgencyFeeVatAmount() { return paymentAndSettlementAgencyFeeVatAmount; }
    public Money platformFeeAmount() { return platformFeeAmount; }
    public Money platformFeeVatAmount() { return platformFeeVatAmount; }
    public Money otherDeductionAmount() { return otherDeductionAmount; }
    public Money creatorPayoutAmount() { return creatorPayoutAmount; }
    public LocalDateTime confirmedAt() { return confirmedAt; }

    @PostLoad
    private void validateState() {
        validatePositive(projectId, "프로젝트 식별자는 양수여야 합니다.");
        validatePositive(creatorId, "창작자 식별자는 양수여야 합니다.");
        requireMoney(baseAmount, "프로젝트 정산 기준 금액");
        if (baseAmount.amount().signum() == 0) {
            throw new IllegalArgumentException("프로젝트 정산 기준 금액은 0원보다 커야 합니다.");
        }
        requireMoney(paymentAndSettlementAgencyFeeAmount, "결제·정산 대행 수수료");
        requireMoney(paymentAndSettlementAgencyFeeVatAmount, "결제·정산 대행 수수료 부가세");
        requireMoney(platformFeeAmount, "플랫폼 수수료");
        requireMoney(platformFeeVatAmount, "플랫폼 수수료 부가세");
        requireMoney(otherDeductionAmount, "기타 공제액");
        requireMoney(creatorPayoutAmount, "창작자 지급액");
        if (paymentAndSettlementAgencyFeeAmount.amount().compareTo(
                applyRate(baseAmount.amount(), paymentAndSettlementAgencyFeeRate)) > 0
                || !paymentAndSettlementAgencyFeeVatAmount.equals(Money.wons(applyRate(paymentAndSettlementAgencyFeeAmount.amount(), vatRate)))
                || !platformFeeAmount.equals(Money.wons(applyRate(baseAmount.amount(), platformFeeRate)))
                || !platformFeeVatAmount.equals(Money.wons(applyRate(platformFeeAmount.amount(), vatRate)))) {
            throw new IllegalArgumentException("확정 요율과 수수료 계산 결과가 일치하지 않습니다.");
        }
        if (!baseAmount.minus(paymentAndSettlementAgencyFeeAmount)
                .minus(paymentAndSettlementAgencyFeeVatAmount)
                .minus(platformFeeAmount)
                .minus(platformFeeVatAmount)
                .minus(otherDeductionAmount)
                .equals(creatorPayoutAmount)) {
            throw new IllegalArgumentException("창작자 지급액이 공제 후 금액과 일치하지 않습니다.");
        }
        Objects.requireNonNull(confirmedAt, "정산 확정 시각은 필수입니다.");
    }

    private static BigDecimal applyRate(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(0, RoundingMode.DOWN);
    }

    private static void validatePositive(Long value, String message) {
        if (value == null || value <= 0) throw new IllegalArgumentException(message);
    }

    private static void requireMoney(Money money, String name) {
        Objects.requireNonNull(money, name + "은 필수입니다.");
    }
}
