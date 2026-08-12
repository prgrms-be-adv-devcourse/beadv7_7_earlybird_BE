package com.growmighty.lectures.firstday.settlement.domain.model;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(
        name = "project_settlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_settlement_project_id", columnNames = "project_id")
)
public class ProjectSettlement extends BaseEntity {

    private static final BigDecimal PAYMENT_AND_SETTLEMENT_AGENCY_FEE_RATE = new BigDecimal("0.04");
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.04");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");
    private static final BigDecimal ONE = BigDecimal.ONE;

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

    @Column(name = "destination_toss_seller_id", nullable = false, updatable = false, length = 100)
    private String tossSellerId;

    @Column(name = "destination_bank_code", nullable = false, updatable = false, length = 20)
    private String bankCode;

    @Column(name = "destination_masked_account_number", nullable = false, updatable = false, length = 100)
    private String maskedAccountNumber;

    @Column(name = "scheduled_date", nullable = false, updatable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PayoutStatus status;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL)
    @OrderBy("sequence ASC")
    private List<PayoutAttempt> attempts = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "successful_attempt_id", unique = true)
    private PayoutAttempt successfulAttempt;

    @Version
    private Long version;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    protected ProjectSettlement() {
    }

    private ProjectSettlement(
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
            LocalDateTime confirmedAt
    ) {
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
        this.status = PayoutStatus.SCHEDULED;
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

    public List<PayoutAttempt> attempts() {
        return List.copyOf(attempts);
    }

    public int attemptCount() {
        return attempts.size();
    }

    public Optional<PayoutAttempt> latestAttempt() {
        return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.getLast());
    }

    public Optional<PayoutAttempt> successfulAttempt() {
        return Optional.ofNullable(successfulAttempt);
    }

    public Integer successfulAttemptSequence() {
        return successfulAttempt == null ? null : successfulAttempt.sequence();
    }

    public Long version() {
        return version;
    }

    public PayoutAttempt startAttempt(
            String refPayoutId,
            String idempotencyKey,
            LocalDateTime requestedAt
    ) {
        if (status != PayoutStatus.SCHEDULED && status != PayoutStatus.RETRY_WAITING) {
            throw new IllegalStateException("현재 상태에서는 지급 시도를 시작할 수 없습니다: " + status);
        }
        PayoutAttempt attempt = PayoutAttempt.requested(
                this,
                attempts.size() + 1,
                refPayoutId,
                idempotencyKey,
                creatorPayoutAmount,
                requestedAt
        );
        attempts.add(attempt);
        status = PayoutStatus.PROCESSING;
        return attempt;
    }

    public void failAttempt(
            PayoutAttempt attempt,
            String tossPayoutId,
            String errorCode,
            LocalDateTime completedAt,
            boolean retryable
    ) {
        requireProcessingAttempt(attempt);
        attempt.fail(tossPayoutId, errorCode, completedAt);
        status = retryable ? PayoutStatus.RETRY_WAITING : PayoutStatus.ACTION_REQUIRED;
    }

    public void acknowledgeAttempt(
            PayoutAttempt attempt,
            String tossPayoutId,
            PayoutAttemptStatus acknowledgedStatus
    ) {
        requireProcessingAttempt(attempt);
        attempt.acknowledge(tossPayoutId, acknowledgedStatus);
    }

    public void completeAttempt(
            PayoutAttempt attempt,
            String tossPayoutId,
            LocalDateTime completedAt
    ) {
        requireProcessingAttempt(attempt);
        if (successfulAttempt != null) {
            throw new IllegalStateException("이미 성공한 지급 시도가 존재합니다.");
        }
        attempt.complete(tossPayoutId, completedAt);
        successfulAttempt = attempt;
        status = PayoutStatus.COMPLETED;
    }

    public void markAttemptUnknown(PayoutAttempt attempt) {
        requireProcessingAttempt(attempt);
        attempt.markUnknown(null);
    }

    public void cancelAttempt(
            PayoutAttempt attempt,
            String tossPayoutId,
            LocalDateTime completedAt
    ) {
        requireProcessingAttempt(attempt);
        attempt.cancel(tossPayoutId, completedAt);
        status = PayoutStatus.ACTION_REQUIRED;
    }

    public LocalDateTime confirmedAt() {
        return confirmedAt;
    }

    @PostLoad
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
        Set<Integer> sequences = new HashSet<>();
        for (PayoutAttempt attempt : attempts) {
            if (!creatorPayoutAmount.equals(attempt.amount())) {
                throw new IllegalArgumentException("지급 시도의 금액이 프로젝트 정산 지급액과 일치하지 않습니다.");
            }
            if (!sequences.add(attempt.sequence())) {
                throw new IllegalArgumentException("지급 시도 순번은 중복될 수 없습니다.");
            }
        }
        long completedAttempts = attempts.stream()
                .filter(attempt -> attempt.status() == PayoutAttemptStatus.COMPLETED)
                .count();
        if (completedAttempts > 1) {
            throw new IllegalArgumentException("성공한 지급 시도는 하나만 존재할 수 있습니다.");
        }
        if (successfulAttempt != null && !attempts.contains(successfulAttempt)) {
            throw new IllegalArgumentException("성공한 지급 시도가 지급 시도 목록에 없습니다.");
        }
        if ((status == PayoutStatus.COMPLETED) != (successfulAttempt != null)) {
            throw new IllegalArgumentException("지급 완료 상태와 성공한 지급 시도가 일치해야 합니다.");
        }
        if (successfulAttempt != null && successfulAttempt.status() != PayoutAttemptStatus.COMPLETED) {
            throw new IllegalArgumentException("성공한 지급 시도는 완료 상태여야 합니다.");
        }
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

    private void requireProcessingAttempt(PayoutAttempt attempt) {
        if (status != PayoutStatus.PROCESSING || !attempts.contains(attempt)) {
            throw new IllegalStateException("현재 프로젝트 정산에 처리 중인 지급 시도가 아닙니다.");
        }
    }
}
