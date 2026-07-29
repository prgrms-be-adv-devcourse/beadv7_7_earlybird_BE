package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessment;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessmentReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutSchedulePolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ProjectSettlementRunService {

    private final ProjectOutcomeReader projectOutcomeReader;
    private final ProjectOrderReader projectOrderReader;
    private final PaymentAssessmentReader paymentAssessmentReader;
    private final ProjectSettlementService projectSettlementService;
    private final Clock clock;
    private final Optional<PayoutExecutor> payoutExecutor;

    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            ProjectOrderReader projectOrderReader,
            PaymentAssessmentReader paymentAssessmentReader,
            ProjectSettlementService projectSettlementService,
            Clock clock
    ) {
        this.projectOutcomeReader = projectOutcomeReader;
        this.projectOrderReader = projectOrderReader;
        this.paymentAssessmentReader = paymentAssessmentReader;
        this.projectSettlementService = projectSettlementService;
        this.clock = clock;
        this.payoutExecutor = Optional.empty();
    }

    @Autowired
    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            ProjectOrderReader projectOrderReader,
            PaymentAssessmentReader paymentAssessmentReader,
            ProjectSettlementService projectSettlementService,
            Clock clock,
            Optional<PayoutExecutor> payoutExecutor
    ) {
        this.projectOutcomeReader = projectOutcomeReader;
        this.projectOrderReader = projectOrderReader;
        this.paymentAssessmentReader = paymentAssessmentReader;
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
                paymentAmounts = findFinalEffectivePaymentAmounts(outcome.projectId());
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

    private List<Money> findFinalEffectivePaymentAmounts(Long projectId) {
        List<ProjectOrders> orderResults = List.copyOf(
                projectOrderReader.findProjectOrders(Set.of(projectId))
        );
        if (orderResults.size() != 1 || !projectId.equals(orderResults.getFirst().projectId())) {
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
        }
        ProjectOrders projectOrders = orderResults.getFirst();
        if (projectOrders.orderIds().isEmpty()) {
            return List.of();
        }
        Set<Long> orderIds = Set.copyOf(projectOrders.orderIds());
        List<PaymentAssessment> assessments = List.copyOf(
                paymentAssessmentReader.findPaymentAssessments(orderIds)
        );
        Map<Long, PaymentAssessment> assessmentByOrderId = new HashMap<>();
        for (PaymentAssessment assessment : assessments) {
            if (assessment == null || assessmentByOrderId.put(assessment.orderId(), assessment) != null) {
                throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
            }
        }
        if (!new HashSet<>(assessmentByOrderId.keySet()).equals(orderIds)) {
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
        }
        return projectOrders.orderIds().stream()
                .map(assessmentByOrderId::get)
                .map(ProjectSettlementRunService::finalEffectiveAmount)
                .toList();
    }

    private static Money finalEffectiveAmount(PaymentAssessment assessment) {
        return switch (assessment) {
            case PaymentAssessment.Ready ready -> ready.finalEffectiveAmount();
            case PaymentAssessment.NoPayment ignored -> Money.wons(0);
            case PaymentAssessment.NotReady ignored ->
                    throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
        };
    }

    private static boolean isInvalid(ProjectOutcome outcome) {
        return outcome == null
                || outcome.projectId() == null || outcome.projectId() <= 0
                || outcome.creatorId() == null || outcome.creatorId() <= 0
                || outcome.status() == null;
    }
}
