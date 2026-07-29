package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SETTLEMENT_DATA_INCONSISTENT;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(
        name = "settlement.toss-payout.enabled",
        havingValue = "true"
)
public final class PayoutExecutionService implements PayoutExecutor {

    private static final int MAX_ATTEMPTS = 4;
    private static final String TRANSACTION_DESCRIPTION = "얼리버드";
    private static final Set<String> SAME_ATTEMPT_ERROR_CODES = Set.of(
            "PREVIOUS_REQUEST_IN_PROGRESS",
            "IDEMPOTENT_REQUEST_PROCESSING"
    );
    private static final Set<String> RETRYABLE_ERROR_CODES = Set.of(
            "COMMON_ERROR",
            "EXCEEDED_PAYOUT_BALANCE_AMOUNT"
    );

    private final PayoutObligationRepository payoutObligationRepository;
    private final ProjectSettlementRepository projectSettlementRepository;
    private final PayoutGateway payoutGateway;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    public PayoutExecutionService(
            PayoutObligationRepository payoutObligationRepository,
            ProjectSettlementRepository projectSettlementRepository,
            PayoutGateway payoutGateway,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(
                payoutObligationRepository,
                projectSettlementRepository,
                payoutGateway,
                new TransactionTemplate(transactionManager),
                clock
        );
    }

    PayoutExecutionService(
            PayoutObligationRepository payoutObligationRepository,
            ProjectSettlementRepository projectSettlementRepository,
            PayoutGateway payoutGateway,
            TransactionOperations transactions,
            Clock clock
    ) {
        this.payoutObligationRepository = Objects.requireNonNull(
                payoutObligationRepository,
                "지급 의무 저장소는 필수입니다."
        );
        this.projectSettlementRepository = Objects.requireNonNull(
                projectSettlementRepository,
                "프로젝트 정산 저장소는 필수입니다."
        );
        this.payoutGateway = Objects.requireNonNull(payoutGateway, "지급대행 외부 연동은 필수입니다.");
        this.transactions = Objects.requireNonNull(transactions, "지급 트랜잭션 실행기는 필수입니다.");
        this.clock = Objects.requireNonNull(clock, "지급 처리 Clock은 필수입니다.");
    }

    @Override
    public PayoutExecutionResult execute(Long payoutObligationId) {
        if (payoutObligationId == null || payoutObligationId <= 0) {
            throw new IllegalArgumentException("지급 의무 식별자는 양수여야 합니다.");
        }

        PreparedPayout prepared = requireTransactionResult(
                transactions.execute(status -> prepare(payoutObligationId))
        );
        if (prepared.request() == null) {
            return prepared.currentResult();
        }

        PayoutGatewayResult gatewayResult;
        try {
            gatewayResult = payoutGateway.requestScheduledPayout(prepared.request());
        } catch (PayoutGatewayException exception) {
            return requireTransactionResult(transactions.execute(status -> markUnknown(prepared)));
        }

        return requireTransactionResult(
                transactions.execute(status -> applyGatewayResult(prepared, gatewayResult))
        );
    }

    private PreparedPayout prepare(Long payoutObligationId) {
        PayoutObligation obligation = findObligation(payoutObligationId);
        PayoutAttempt attempt;

        if (obligation.status() == PayoutObligationStatus.SCHEDULED
                || obligation.status() == PayoutObligationStatus.RETRY_WAITING) {
            int sequence = obligation.attemptCount() + 1;
            attempt = obligation.startAttempt(
                    refPayoutId(obligation.id(), sequence),
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(clock)
            );
            obligation = payoutObligationRepository.save(obligation);
            attempt = findAttempt(obligation, sequence);
        } else if (obligation.status() == PayoutObligationStatus.PROCESSING) {
            attempt = obligation.latestAttempt()
                    .orElseThrow(PayoutExecutionService::inconsistentData);
            if (attempt.status() != PayoutAttemptStatus.REQUESTED
                    && attempt.status() != PayoutAttemptStatus.IN_PROGRESS
                    && attempt.status() != PayoutAttemptStatus.UNKNOWN) {
                throw inconsistentData();
            }
        } else {
            return PreparedPayout.noRequest(resultOf(obligation));
        }

        ProjectSettlement settlement = projectSettlementRepository.findById(obligation.settlementId())
                .orElseThrow(PayoutExecutionService::inconsistentData);
        validatePayoutSource(obligation, settlement);

        ScheduledPayoutRequest request = new ScheduledPayoutRequest(
                attempt.refPayoutId(),
                settlement.destinationSnapshot().tossSellerId(),
                obligation.scheduledDate(),
                obligation.amount(),
                TRANSACTION_DESCRIPTION,
                attempt.idempotencyKey()
        );
        return PreparedPayout.requested(
                obligation.id(),
                attempt.sequence(),
                request
        );
    }

    private PayoutExecutionResult applyGatewayResult(
            PreparedPayout prepared,
            PayoutGatewayResult gatewayResult
    ) {
        PayoutObligation obligation = findObligation(prepared.payoutObligationId());
        if (obligation.status() != PayoutObligationStatus.PROCESSING) {
            return resultOf(obligation);
        }
        PayoutAttempt attempt = findAttempt(obligation, prepared.attemptSequence());

        if (gatewayResult instanceof PayoutGatewayResult.Rejected rejected) {
            applyFailure(obligation, attempt, null, rejected.errorCode());
        } else if (gatewayResult instanceof PayoutGatewayResult.Accepted accepted) {
            applyAcceptedResult(obligation, attempt, accepted);
        } else {
            throw new PayoutGatewayException("지원하지 않는 지급대행 결과입니다.");
        }

        return resultOf(payoutObligationRepository.save(obligation));
    }

    private PayoutExecutionResult markUnknown(PreparedPayout prepared) {
        PayoutObligation obligation = findObligation(prepared.payoutObligationId());
        if (obligation.status() != PayoutObligationStatus.PROCESSING) {
            return resultOf(obligation);
        }
        PayoutAttempt attempt = findAttempt(obligation, prepared.attemptSequence());
        obligation.markAttemptUnknown(attempt);
        return resultOf(payoutObligationRepository.save(obligation));
    }

    private void applyAcceptedResult(
            PayoutObligation obligation,
            PayoutAttempt attempt,
            PayoutGatewayResult.Accepted accepted
    ) {
        switch (accepted.status()) {
            case REQUESTED, IN_PROGRESS -> obligation.acknowledgeAttempt(
                    attempt,
                    accepted.payoutId(),
                    accepted.status()
            );
            case COMPLETED -> obligation.completeAttempt(
                    attempt,
                    accepted.payoutId(),
                    LocalDateTime.now(clock)
            );
            case FAILED -> applyFailure(
                    obligation,
                    attempt,
                    accepted.payoutId(),
                    accepted.errorCode()
            );
            case CANCELED -> obligation.cancelAttempt(
                    attempt,
                    accepted.payoutId(),
                    LocalDateTime.now(clock)
            );
            case UNKNOWN -> throw new PayoutGatewayException(
                    "결과 불명확 상태는 토스 확정 응답으로 반영할 수 없습니다."
            );
        }
    }

    private void applyFailure(
            PayoutObligation obligation,
            PayoutAttempt attempt,
            String tossPayoutId,
            String errorCode
    ) {
        if (SAME_ATTEMPT_ERROR_CODES.contains(errorCode)) {
            obligation.markAttemptUnknown(attempt, errorCode);
            return;
        }
        boolean retryable = RETRYABLE_ERROR_CODES.contains(errorCode)
                && obligation.attemptCount() < MAX_ATTEMPTS;
        obligation.failAttempt(
                attempt,
                tossPayoutId,
                errorCode,
                LocalDateTime.now(clock),
                retryable
        );
    }

    private PayoutObligation findObligation(Long payoutObligationId) {
        return payoutObligationRepository.findById(payoutObligationId)
                .orElseThrow(PayoutExecutionService::inconsistentData);
    }

    private static PayoutAttempt findAttempt(PayoutObligation obligation, int sequence) {
        return obligation.attempts().stream()
                .filter(attempt -> attempt.sequence() == sequence)
                .findFirst()
                .orElseThrow(PayoutExecutionService::inconsistentData);
    }

    private static PayoutExecutionResult resultOf(PayoutObligation obligation) {
        PayoutAttempt attempt = obligation.latestAttempt()
                .orElseThrow(PayoutExecutionService::inconsistentData);
        return new PayoutExecutionResult(
                obligation.id(),
                attempt.sequence(),
                attempt.status(),
                obligation.status()
        );
    }

    private static void validatePayoutSource(
            PayoutObligation obligation,
            ProjectSettlement settlement
    ) {
        if (!Objects.equals(obligation.settlementId(), settlement.id())
                || !Objects.equals(obligation.creatorId(), settlement.creatorId())
                || !Objects.equals(obligation.amount(), settlement.creatorPayoutAmount())) {
            throw inconsistentData();
        }
    }

    private static String refPayoutId(Long payoutObligationId, int sequence) {
        return "earlybird-payout-" + payoutObligationId + "-" + sequence;
    }

    private static SettlementException inconsistentData() {
        return new SettlementException(SETTLEMENT_DATA_INCONSISTENT);
    }

    private static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw inconsistentData();
        }
        return result;
    }

    private record PreparedPayout(
            Long payoutObligationId,
            int attemptSequence,
            ScheduledPayoutRequest request,
            PayoutExecutionResult currentResult
    ) {

        private static PreparedPayout requested(
                Long payoutObligationId,
                int attemptSequence,
                ScheduledPayoutRequest request
        ) {
            return new PreparedPayout(
                    payoutObligationId,
                    attemptSequence,
                    Objects.requireNonNull(request),
                    null
            );
        }

        private static PreparedPayout noRequest(PayoutExecutionResult currentResult) {
            return new PreparedPayout(
                    currentResult.payoutObligationId(),
                    currentResult.attemptSequence(),
                    null,
                    currentResult
            );
        }
    }
}
