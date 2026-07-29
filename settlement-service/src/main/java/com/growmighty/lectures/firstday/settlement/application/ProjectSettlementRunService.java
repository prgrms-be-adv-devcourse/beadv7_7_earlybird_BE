package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.FinalEffectivePaymentAmountReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutSchedulePolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ProjectSettlementRunService {

    private final ProjectOutcomeReader projectOutcomeReader;
    private final FinalEffectivePaymentAmountReader finalEffectivePaymentAmountReader;
    private final ProjectSettlementService projectSettlementService;
    private final Clock clock;
    private final Optional<PayoutExecutor> payoutExecutor;

    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            FinalEffectivePaymentAmountReader finalEffectivePaymentAmountReader,
            ProjectSettlementService projectSettlementService,
            Clock clock
    ) {
        this(
                projectOutcomeReader,
                finalEffectivePaymentAmountReader,
                projectSettlementService,
                clock,
                Optional.empty()
        );
    }

    @Autowired
    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            FinalEffectivePaymentAmountReader finalEffectivePaymentAmountReader,
            ProjectSettlementService projectSettlementService,
            Clock clock,
            Optional<PayoutExecutor> payoutExecutor
    ) {
        this.projectOutcomeReader = projectOutcomeReader;
        this.finalEffectivePaymentAmountReader = finalEffectivePaymentAmountReader;
        this.projectSettlementService = projectSettlementService;
        this.clock = clock;
        this.payoutExecutor = payoutExecutor;
    }

    public ProjectSettlementRunResult run(YearMonth settlementMonth) {
        return run(new RunProjectSettlementsCommand(
                settlementMonth,
                PayoutSchedulePolicy.current().scheduledDateFor(settlementMonth),
                LocalDateTime.now(clock)
        ));
    }

    public ProjectSettlementRunResult run(RunProjectSettlementsCommand command) {
        List<ProjectOutcome> outcomes;
        try {
            outcomes = projectOutcomeReader.findProjectOutcomes();
        } catch (SettlementException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE, exception);
        }
        try {
            outcomes = List.copyOf(outcomes);
        } catch (NullPointerException exception) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
        }
        if (outcomes.stream().anyMatch(ProjectSettlementRunService::isInvalid)) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
        }
        List<ConfirmedProjectSettlement> confirmedSettlements = new ArrayList<>();

        for (ProjectOutcome outcome : outcomes) {
            if (outcome.status() != ProjectOutcomeStatus.SUCCEEDED) {
                continue;
            }
            List<Money> paymentAmounts;
            try {
                paymentAmounts = finalEffectivePaymentAmountReader
                        .findFinalEffectivePaymentAmounts(outcome.projectId());
            } catch (SettlementException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE, exception);
            }
            try {
                paymentAmounts = List.copyOf(paymentAmounts);
            } catch (NullPointerException exception) {
                throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
            }
            ConfirmProjectSettlementCommand confirmCommand = new ConfirmProjectSettlementCommand(
                    outcome.projectId(),
                    outcome.creatorId(),
                    paymentAmounts,
                    command.scheduledDate(),
                    command.confirmedAt()
            );
            ConfirmedProjectSettlement confirmed = projectSettlementService.confirm(confirmCommand);
            if (payoutExecutor.isPresent()) {
                PayoutExecutionResult payoutResult = payoutExecutor.get()
                        .execute(confirmed.payoutObligationId());
                confirmed = confirmed.withPayoutObligationStatus(
                        payoutResult.payoutObligationStatus()
                );
            }
            confirmedSettlements.add(confirmed);
        }

        return new ProjectSettlementRunResult(command.settlementMonth(), confirmedSettlements);
    }

    private static boolean isInvalid(ProjectOutcome outcome) {
        return outcome == null
                || outcome.projectId() == null || outcome.projectId() <= 0
                || outcome.creatorId() == null || outcome.creatorId() <= 0
                || outcome.status() == null;
    }
}
