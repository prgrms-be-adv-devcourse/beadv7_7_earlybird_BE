package com.growmighty.lectures.firstday.settlement.application.run;

import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutor;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmProjectSettlementCommand;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPayoutRun;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPayoutInputRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPayoutRunRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectPayoutRunService {

    private final ProjectPayoutInputRepository inputRepository;
    private final ProjectPayoutRunRepository runRepository;
    private final ProjectSettlementService projectSettlementService;
    private final PayoutExecutor payoutExecutor;
    private final Clock clock;

    public void run(YearMonth payoutMonth) {
        if (runRepository.findRunningByPayoutMonth(payoutMonth).isPresent()) {
            return;
        }

        ProjectPayoutRun run = runRepository.save(ProjectPayoutRun.start(payoutMonth, LocalDateTime.now(clock)));
        try {
            for (ProjectOutcomeFact outcome : inputRepository.findSucceededProjects()) {
                List<OrderPaymentFact> payments = inputRepository.findCompletedPaymentsByProjectId(outcome.projectId());
                if (payments.stream().allMatch(this::reconciled)) {
                    Long settlementId = projectSettlementService.confirm(new ConfirmProjectSettlementCommand(
                            outcome.projectId(),
                            outcome.creatorId(),
                            payments.stream().map(OrderPaymentFact::paymentAmount).toList(),
                            payoutMonth.atDay(5),
                            LocalDateTime.now(clock)
                    )).settlementId();
                    payoutExecutor.execute(settlementId);
                }
            }
            run.complete(LocalDateTime.now(clock));
            runRepository.save(run);
        } catch (RuntimeException exception) {
            run.fail(LocalDateTime.now(clock));
            runRepository.save(run);
            throw exception;
        }
    }

    private boolean reconciled(OrderPaymentFact payment) {
        return payment.reconciliationStatus() == OrderPaymentFact.ReconciliationStatus.CONFIRMED;
    }
}
