package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.application.port.FinalEffectivePaymentAmountReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
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
        List<ProjectSettlementTarget> targets = projectSettlementTargetReader
                .findSettlementTargets(command.settlementMonth());
        List<ConfirmedProjectSettlement> confirmedSettlements = new ArrayList<>();

        for (ProjectSettlementTarget target : targets) {
            ConfirmProjectSettlementCommand confirmCommand = new ConfirmProjectSettlementCommand(
                    target.projectId(),
                    target.creatorId(),
                    finalEffectivePaymentAmountReader.findFinalEffectivePaymentAmounts(target.projectId()),
                    command.scheduledDate(),
                    command.confirmedAt()
            );
            confirmedSettlements.add(projectSettlementService.confirm(confirmCommand));
        }

        return new ProjectSettlementRunResult(command.settlementMonth(), confirmedSettlements);
    }
}
