package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.FinalEffectivePaymentAmountReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutSchedulePolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class ProjectSettlementRunService {

    private final ProjectSettlementTargetReader projectSettlementTargetReader;
    private final FinalEffectivePaymentAmountReader finalEffectivePaymentAmountReader;
    private final ProjectSettlementService projectSettlementService;
    private final Clock clock;

    public ProjectSettlementRunService(
            ProjectSettlementTargetReader projectSettlementTargetReader,
            FinalEffectivePaymentAmountReader finalEffectivePaymentAmountReader,
            ProjectSettlementService projectSettlementService,
            Clock clock
    ) {
        this.projectSettlementTargetReader = projectSettlementTargetReader;
        this.finalEffectivePaymentAmountReader = finalEffectivePaymentAmountReader;
        this.projectSettlementService = projectSettlementService;
        this.clock = clock;
    }

    public ProjectSettlementRunResult run(YearMonth settlementMonth) {
        return run(new RunProjectSettlementsCommand(
                settlementMonth,
                PayoutSchedulePolicy.current().scheduledDateFor(settlementMonth),
                LocalDateTime.now(clock)
        ));
    }

    public ProjectSettlementRunResult run(RunProjectSettlementsCommand command) {
        List<ProjectSettlementTarget> targets;
        try {
            targets = projectSettlementTargetReader.findSettlementTargets(command.settlementMonth());
        } catch (SettlementException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE, exception);
        }
        if (targets == null) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
        }
        List<ConfirmedProjectSettlement> confirmedSettlements = new ArrayList<>();

        for (ProjectSettlementTarget target : targets) {
            if (target == null
                    || target.projectId() == null || target.projectId() <= 0
                    || target.creatorId() == null || target.creatorId() <= 0) {
                throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
            }
            List<Money> paymentAmounts;
            try {
                paymentAmounts = finalEffectivePaymentAmountReader
                        .findFinalEffectivePaymentAmounts(target.projectId());
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
                    target.projectId(),
                    target.creatorId(),
                    paymentAmounts,
                    command.scheduledDate(),
                    command.confirmedAt()
            );
            confirmedSettlements.add(projectSettlementService.confirm(confirmCommand));
        }

        return new ProjectSettlementRunResult(command.settlementMonth(), confirmedSettlements);
    }
}
