package com.growmighty.lectures.firstday.settlement.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class ProjectSettlement {

    private static final BigDecimal PAYMENT_AND_SETTLEMENT_AGENCY_FEE_RATE = new BigDecimal("0.04");
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.04");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final Long id;
    private final Long projectId;
    private final Long creatorId;
    private final BigDecimal paymentAndSettlementAgencyFeeRate;
    private final BigDecimal platformFeeRate;
    private final BigDecimal vatRate;
    private final Money baseAmount;
    private final Money paymentAndSettlementAgencyFeeAmount;
    private final Money paymentAndSettlementAgencyFeeVatAmount;
    private final Money platformFeeAmount;
    private final Money platformFeeVatAmount;
    private final Money otherDeductionAmount;
    private final Money creatorPayoutAmount;
    private final String tossSellerId;
    private final String bankCode;
    private final String maskedAccountNumber;
    private final LocalDate scheduledDate;
    private final PayoutStatus status;
    private final LocalDateTime confirmedAt;

    private ProjectSettlement(
            Long id,
            Long projectId,
            Long creatorId,
            BigDecimal paymentAndSettlementAgencyFeeRate,
            BigDecimal platformFeeRate,
            BigDecimal vatRate,
            Money baseAmount,
            Money paymentAndSettlementAgencyFeeAmount,
            Money paymentAndSettlementAgencyFeeVatAmount,
            Money platformFeeAmount,
            Money platformFeeVatAmount,
            Money otherDeductionAmount,
            Money creatorPayoutAmount,
            String tossSellerId,
            String bankCode,
            String maskedAccountNumber,
            LocalDate scheduledDate,
            PayoutStatus status,
            LocalDateTime confirmedAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.creatorId = creatorId;
        this.paymentAndSettlementAgencyFeeRate = normalizeRate(
                paymentAndSettlementAgencyFeeRate,
                "결제·정산 대행 수수료율"
        );
        this.platformFeeRate = normalizeRate(platformFeeRate, "플랫폼 수수료율");
        this.vatRate = normalizeRate(vatRate, "부가가치세율");
        this.baseAmount = baseAmount;
        this.paymentAndSettlementAgencyFeeAmount = paymentAndSettlementAgencyFeeAmount;
        this.paymentAndSettlementAgencyFeeVatAmount = paymentAndSettlementAgencyFeeVatAmount;
        this.platformFeeAmount = platformFeeAmount;
        this.platformFeeVatAmount = platformFeeVatAmount;
        this.otherDeductionAmount = otherDeductionAmount;
        this.creatorPayoutAmount = creatorPayoutAmount;
        this.tossSellerId = tossSellerId;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
        this.scheduledDate = scheduledDate;
        this.status = status;
        this.confirmedAt = confirmedAt;
        validateState();
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

        Money baseAmount = Money.wons(amounts.stream()
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
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
        Money creatorPayoutAmount = baseAmount
                .minus(agencyFeeAmount)
                .minus(agencyFeeVatAmount)
                .minus(platformFeeAmount)
                .minus(platformFeeVatAmount)
                .minus(otherDeductionAmount);

        return new ProjectSettlement(
                null,
                projectId,
                creatorId,
                PAYMENT_AND_SETTLEMENT_AGENCY_FEE_RATE,
                PLATFORM_FEE_RATE,
                VAT_RATE,
                baseAmount,
                agencyFeeAmount,
                agencyFeeVatAmount,
                platformFeeAmount,
                platformFeeVatAmount,
                otherDeductionAmount,
                creatorPayoutAmount,
                profile.tossSellerId(),
                profile.bankCode(),
                profile.maskedAccountNumber(),
                scheduledDate,
                PayoutStatus.SCHEDULED,
                confirmedAt
        );
    }

    public static ProjectSettlement restore(
            Long id,
            Long projectId,
            Long creatorId,
            BigDecimal paymentAndSettlementAgencyFeeRate,
            BigDecimal platformFeeRate,
            BigDecimal vatRate,
            Money baseAmount,
            Money paymentAndSettlementAgencyFeeAmount,
            Money paymentAndSettlementAgencyFeeVatAmount,
            Money platformFeeAmount,
            Money platformFeeVatAmount,
            Money otherDeductionAmount,
            Money creatorPayoutAmount,
            String tossSellerId,
            String bankCode,
            String maskedAccountNumber,
            LocalDate scheduledDate,
            PayoutStatus status,
            LocalDateTime confirmedAt
    ) {
        return new ProjectSettlement(
                Objects.requireNonNull(id, "프로젝트 정산 식별자는 필수입니다."),
                projectId,
                creatorId,
                paymentAndSettlementAgencyFeeRate,
                platformFeeRate,
                vatRate,
                baseAmount,
                paymentAndSettlementAgencyFeeAmount,
                paymentAndSettlementAgencyFeeVatAmount,
                platformFeeAmount,
                platformFeeVatAmount,
                otherDeductionAmount,
                creatorPayoutAmount,
                tossSellerId,
                bankCode,
                maskedAccountNumber,
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

    public BigDecimal paymentAndSettlementAgencyFeeRate() {
        return paymentAndSettlementAgencyFeeRate;
    }

    public BigDecimal platformFeeRate() {
        return platformFeeRate;
    }

    public BigDecimal vatRate() {
        return vatRate;
    }

    public Money baseAmount() {
        return baseAmount;
    }

    public Money paymentAndSettlementAgencyFeeAmount() {
        return paymentAndSettlementAgencyFeeAmount;
    }

    public Money paymentAndSettlementAgencyFeeVatAmount() {
        return paymentAndSettlementAgencyFeeVatAmount;
    }

    public Money platformFeeAmount() {
        return platformFeeAmount;
    }

    public Money platformFeeVatAmount() {
        return platformFeeVatAmount;
    }

    public Money otherDeductionAmount() {
        return otherDeductionAmount;
    }

    public Money creatorPayoutAmount() {
        return creatorPayoutAmount;
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

    public LocalDate scheduledDate() {
        return scheduledDate;
    }

    public PayoutStatus status() {
        return status;
    }

    public LocalDateTime confirmedAt() {
        return confirmedAt;
    }

    private void validateState() {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("프로젝트 정산 식별자는 양수여야 합니다.");
        }
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
                applyRate(baseAmount.amount(), paymentAndSettlementAgencyFeeRate)
        ) > 0
                || !paymentAndSettlementAgencyFeeVatAmount.equals(Money.wons(applyRate(
                paymentAndSettlementAgencyFeeAmount.amount(),
                vatRate
        )))
                || !platformFeeAmount.equals(Money.wons(applyRate(baseAmount.amount(), platformFeeRate)))
                || !platformFeeVatAmount.equals(Money.wons(applyRate(platformFeeAmount.amount(), vatRate)))) {
            throw new IllegalArgumentException("확정 요율과 수수료 계산 결과가 일치하지 않습니다.");
        }
        Money expectedPayoutAmount = baseAmount
                .minus(paymentAndSettlementAgencyFeeAmount)
                .minus(paymentAndSettlementAgencyFeeVatAmount)
                .minus(platformFeeAmount)
                .minus(platformFeeVatAmount)
                .minus(otherDeductionAmount);
        if (!expectedPayoutAmount.equals(creatorPayoutAmount)) {
            throw new IllegalArgumentException("창작자 지급액이 공제 후 금액과 일치하지 않습니다.");
        }
        requireText(tossSellerId, "토스 셀러 식별자");
        requireText(bankCode, "은행 식별 정보");
        requireText(maskedAccountNumber, "마스킹된 계좌번호");
        Objects.requireNonNull(scheduledDate, "지급 예정일은 필수입니다.");
        Objects.requireNonNull(status, "지급 상태는 필수입니다.");
        Objects.requireNonNull(confirmedAt, "정산 확정 시각은 필수입니다.");
    }

    private static BigDecimal applyRate(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(0, RoundingMode.DOWN);
    }

    private static BigDecimal normalizeRate(BigDecimal rate, String name) {
        Objects.requireNonNull(rate, name + "은 필수입니다.");
        if (rate.signum() < 0 || rate.compareTo(ONE) >= 0) {
            throw new IllegalArgumentException(name + "은 0 이상 1 미만이어야 합니다.");
        }
        return rate.stripTrailingZeros();
    }

    private static void validatePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireMoney(Money money, String name) {
        Objects.requireNonNull(money, name + "은 필수입니다.");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "은 필수입니다.");
        }
    }
}
