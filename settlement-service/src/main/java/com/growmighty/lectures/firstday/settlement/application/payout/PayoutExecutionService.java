package com.growmighty.lectures.firstday.settlement.application.payout;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SETTLEMENT_DATA_INCONSISTENT;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.payout.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class PayoutExecutionService implements PayoutExecutor {

    private static final int MAX_ATTEMPTS = 4;
    private static final String TRANSACTION_DESCRIPTION = "얼리버드";

    private final ProjectSettlementRepository projectSettlementRepository;
    private final PayoutGateway payoutGateway;
    private final TransactionOperations transactions;
    private final Clock clock;

    @Autowired
    public PayoutExecutionService(
            ProjectSettlementRepository projectSettlementRepository,
            PayoutGateway payoutGateway,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(
                projectSettlementRepository,
                payoutGateway,
                new TransactionTemplate(transactionManager),
                clock
        );
    }

    PayoutExecutionService(
            ProjectSettlementRepository projectSettlementRepository,
            PayoutGateway payoutGateway,
            TransactionOperations transactions,
            Clock clock
    ) {
        this.projectSettlementRepository = Objects.requireNonNull(
                projectSettlementRepository,
                "프로젝트 정산 저장소는 필수입니다."
        );
        this.payoutGateway = Objects.requireNonNull(payoutGateway, "지급대행 실행기는 필수입니다.");
        this.transactions = Objects.requireNonNull(transactions, "지급 트랜잭션 실행기는 필수입니다.");
        this.clock = Objects.requireNonNull(clock, "지급 처리 Clock은 필수입니다.");
    }

    @Override
    public PayoutExecutionResult execute(Long settlementId) {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException("프로젝트 정산 식별자는 양수여야 합니다.");
        }

        PreparedPayout prepared = requireTransactionResult(
                transactions.execute(status -> prepare(settlementId))
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

    private PreparedPayout prepare(Long settlementId) {
        ProjectSettlement settlement = findSettlement(settlementId);
        PayoutAttempt attempt;

        if (settlement.status() == PayoutStatus.SCHEDULED
                || settlement.status() == PayoutStatus.RETRY_WAITING) {
            int sequence = settlement.attemptCount() + 1;
            attempt = settlement.startAttempt(
                    refPayoutId(settlement.id(), sequence),
                    UUID.randomUUID().toString(),
                    LocalDateTime.now(clock)
            );
            settlement = projectSettlementRepository.save(settlement);
            attempt = findAttempt(settlement, sequence);
        } else if (settlement.status() == PayoutStatus.PROCESSING) {
            attempt = settlement.latestAttempt()
                    .orElseThrow(PayoutExecutionService::inconsistentData);
            if (attempt.status() != PayoutAttemptStatus.REQUESTED
                    && attempt.status() != PayoutAttemptStatus.IN_PROGRESS
                    && attempt.status() != PayoutAttemptStatus.UNKNOWN) {
                throw inconsistentData();
            }
        } else {
            return PreparedPayout.noRequest(resultOf(settlement));
        }

        ScheduledPayoutRequest request = new ScheduledPayoutRequest(
                attempt.refPayoutId(),
                settlement.tossSellerId(),
                settlement.scheduledDate(),
                settlement.creatorPayoutAmount(),
                TRANSACTION_DESCRIPTION,
                attempt.idempotencyKey()
        );
        return PreparedPayout.requested(settlement.id(), attempt.sequence(), request);
    }

    private PayoutExecutionResult applyGatewayResult(
            PreparedPayout prepared,
            PayoutGatewayResult gatewayResult
    ) {
        ProjectSettlement settlement = findSettlement(prepared.settlementId());
        if (settlement.status() != PayoutStatus.PROCESSING) {
            return resultOf(settlement);
        }
        PayoutAttempt attempt = findAttempt(settlement, prepared.attemptSequence());

        if (gatewayResult instanceof PayoutGatewayResult.Failed failed) {
            applyFailure(settlement, attempt, failed);
        } else if (gatewayResult instanceof PayoutGatewayResult.Accepted accepted) {
            applyAcceptedResult(settlement, attempt, accepted);
        } else {
            throw new PayoutGatewayException("지원하지 않는 지급대행 결과입니다.");
        }

        return resultOf(projectSettlementRepository.save(settlement));
    }

    private PayoutExecutionResult markUnknown(PreparedPayout prepared) {
        ProjectSettlement settlement = findSettlement(prepared.settlementId());
        if (settlement.status() != PayoutStatus.PROCESSING) {
            return resultOf(settlement);
        }
        PayoutAttempt attempt = findAttempt(settlement, prepared.attemptSequence());
        settlement.markAttemptUnknown(attempt);
        return resultOf(projectSettlementRepository.save(settlement));
    }

    private void applyAcceptedResult(
            ProjectSettlement settlement,
            PayoutAttempt attempt,
            PayoutGatewayResult.Accepted accepted
    ) {
        switch (accepted.status()) {
            case REQUESTED, IN_PROGRESS -> settlement.acknowledgeAttempt(
                    attempt,
                    accepted.payoutId(),
                    accepted.status()
            );
            case COMPLETED -> settlement.completeAttempt(
                    attempt,
                    accepted.payoutId(),
                    LocalDateTime.now(clock)
            );
            case CANCELED -> settlement.cancelAttempt(
                    attempt,
                    accepted.payoutId(),
                    LocalDateTime.now(clock)
            );
            case FAILED, UNKNOWN -> throw new PayoutGatewayException("지원하지 않는 지급대행 확정 상태입니다.");
        }
    }

    private void applyFailure(
            ProjectSettlement settlement,
            PayoutAttempt attempt,
            PayoutGatewayResult.Failed failed
    ) {
        boolean retryable = failed.retryable() && settlement.attemptCount() < MAX_ATTEMPTS;
        settlement.failAttempt(
                attempt,
                failed.payoutId(),
                failed.errorCode(),
                LocalDateTime.now(clock),
                retryable
        );
    }

    private ProjectSettlement findSettlement(Long settlementId) {
        return projectSettlementRepository.findById(settlementId)
                .orElseThrow(PayoutExecutionService::inconsistentData);
    }

    private static PayoutAttempt findAttempt(ProjectSettlement settlement, int sequence) {
        return settlement.attempts().stream()
                .filter(attempt -> attempt.sequence() == sequence)
                .findFirst()
                .orElseThrow(PayoutExecutionService::inconsistentData);
    }

    private static PayoutExecutionResult resultOf(ProjectSettlement settlement) {
        PayoutAttempt attempt = settlement.latestAttempt()
                .orElseThrow(PayoutExecutionService::inconsistentData);
        return new PayoutExecutionResult(
                settlement.id(),
                attempt.sequence(),
                attempt.status(),
                settlement.status()
        );
    }

    private static String refPayoutId(Long settlementId, int sequence) {
        return "earlybird-payout-" + settlementId + "-" + sequence;
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
            Long settlementId,
            int attemptSequence,
            ScheduledPayoutRequest request,
            PayoutExecutionResult currentResult
    ) {

        private static PreparedPayout requested(
                Long settlementId,
                int attemptSequence,
                ScheduledPayoutRequest request
        ) {
            return new PreparedPayout(
                    settlementId,
                    attemptSequence,
                    Objects.requireNonNull(request),
                    null
            );
        }

        private static PreparedPayout noRequest(PayoutExecutionResult currentResult) {
            return new PreparedPayout(
                    currentResult.settlementId(),
                    currentResult.attemptSequence(),
                    null,
                    currentResult
            );
        }
    }
}
