// TODO(settlement-plan): Replace the large synchronous orchestrator with one monthly-run module over stored facts, reconciliation, and batch refund Outbox.
package com.growmighty.lectures.firstday.settlement.application.run;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutionResult;
import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutor;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmProjectSettlementCommand;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutSchedulePolicy;
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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ProjectSettlementRunService {

    private final ProjectOutcomeReader projectOutcomeReader;
    private final ProjectOrderReader projectOrderReader;
    private final ProjectSettlementService projectSettlementService;
    private final Clock clock;
    private final Optional<PayoutExecutor> payoutExecutor;

    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            ProjectOrderReader projectOrderReader,
            ProjectSettlementService projectSettlementService,
            Clock clock
    ) {
        this(
                projectOutcomeReader,
                projectOrderReader,
                projectSettlementService,
                clock,
                Optional.empty()
        );
    }

    @Autowired
    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            ProjectOrderReader projectOrderReader,
            ProjectSettlementService projectSettlementService,
            Clock clock,
            Optional<PayoutExecutor> payoutExecutor
    ) {
        this.projectOutcomeReader = projectOutcomeReader;
        this.projectOrderReader = projectOrderReader;
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
        if (outcomes.isEmpty()) {
            return new ProjectSettlementRunResult(command.settlementMonth(), List.of(), List.of());
        }

        Map<Long, ConfirmedProjectSettlement> existingSettlements = findExistingSettlements(outcomes);
        List<ProjectOutcome> pendingOutcomes = outcomes.stream()
                .filter(outcome -> outcome.status() == ProjectOutcomeStatus.SUCCEEDED)
                .filter(outcome -> !existingSettlements.containsKey(outcome.projectId()))
                .toList();
        Map<Long, ProjectOrders> ordersByProjectId = pendingOutcomes.isEmpty()
                ? Map.of()
                : findProjectOrders(pendingOutcomes);

        List<ConfirmedProjectSettlement> confirmedSettlements = new ArrayList<>();
        List<ProjectOutcomeProcessingResult> projectResults = new ArrayList<>();
        for (ProjectOutcome outcome : outcomes) {
            ConfirmedProjectSettlement existingSettlement = existingSettlements.get(outcome.projectId());
            if (existingSettlement != null) {
                boolean outcomeMatchesSettlement = outcome.status() == ProjectOutcomeStatus.SUCCEEDED
                        && outcome.creatorId().equals(existingSettlement.creatorId());
                ConfirmedProjectSettlement restored = outcomeMatchesSettlement
                        ? executePayout(existingSettlement)
                        : existingSettlement;
                confirmedSettlements.add(restored);
                projectResults.add(new ProjectOutcomeProcessingResult(
                        outcome.projectId(),
                        outcome.status(),
                        outcomeMatchesSettlement
                                ? ProjectOutcomeProcessingStatus.SETTLEMENT_ALREADY_CONFIRMED
                                : ProjectOutcomeProcessingStatus.OUTCOME_CONFLICT
                ));
                continue;
            }
            if (outcome.status() != ProjectOutcomeStatus.SUCCEEDED) {
                projectResults.add(new ProjectOutcomeProcessingResult(
                        outcome.projectId(),
                        outcome.status(),
                        ProjectOutcomeProcessingStatus.REFUND_REQUEST_PENDING
                ));
                continue;
            }
            ProjectOrders projectOrders = ordersByProjectId.get(outcome.projectId());
            List<Money> orderPaymentAmounts;
            try {
                orderPaymentAmounts = orderPaymentAmounts(projectOrders);
            } catch (SettlementException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
            }
            try {
                orderPaymentAmounts = List.copyOf(orderPaymentAmounts);
            } catch (NullPointerException exception) {
                throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
            }
            ConfirmProjectSettlementCommand confirmCommand = new ConfirmProjectSettlementCommand(
                    outcome.projectId(),
                    outcome.creatorId(),
                    orderPaymentAmounts,
                    command.scheduledDate(),
                    command.confirmedAt()
            );
            ConfirmedProjectSettlement confirmed = projectSettlementService.confirm(confirmCommand);
            confirmed = executePayout(confirmed);
            confirmedSettlements.add(confirmed);
            projectResults.add(ProjectOutcomeProcessingResult.settlementConfirmed(outcome.projectId()));
        }

        return new ProjectSettlementRunResult(
                command.settlementMonth(),
                projectResults,
                confirmedSettlements
        );
    }

    private Map<Long, ConfirmedProjectSettlement> findExistingSettlements(
            List<ProjectOutcome> outcomes
    ) {
        Map<Long, ConfirmedProjectSettlement> existingSettlements = new HashMap<>();
        for (ProjectOutcome outcome : outcomes) {
            projectSettlementService.findConfirmedByProjectId(outcome.projectId())
                    .ifPresent(settlement -> existingSettlements.put(outcome.projectId(), settlement));
        }
        return Map.copyOf(existingSettlements);
    }

    private ConfirmedProjectSettlement executePayout(ConfirmedProjectSettlement settlement) {
        if (payoutExecutor.isEmpty()) {
            return settlement;
        }
        PayoutExecutionResult payoutResult = payoutExecutor.get()
                .execute(settlement.settlementId());
        return settlement.withPayoutStatus(payoutResult.payoutStatus());
    }

    private Map<Long, ProjectOrders> findProjectOrders(List<ProjectOutcome> outcomes) {
        Set<Long> projectIds = outcomes.stream()
                .map(ProjectOutcome::projectId)
                .collect(Collectors.toUnmodifiableSet());
        List<ProjectOrders> orderResults;
        try {
            orderResults = List.copyOf(projectOrderReader.findProjectOrders(projectIds));
        } catch (SettlementException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }
        Map<Long, ProjectOrders> ordersByProjectId = new HashMap<>();
        Set<Long> allOrderIds = new HashSet<>();
        for (ProjectOrders projectOrders : orderResults) {
            if (projectOrders == null
                    || ordersByProjectId.put(projectOrders.projectId(), projectOrders) != null) {
                throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
            }
            for (OrderPayment order : projectOrders.orders()) {
                if (!allOrderIds.add(order.orderId())) {
                    throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
                }
            }
        }
        if (!ordersByProjectId.keySet().equals(projectIds)) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
        }
        validateSuccessfulProjectOrders(outcomes, ordersByProjectId);
        return Map.copyOf(ordersByProjectId);
    }

    private static void validateSuccessfulProjectOrders(
            List<ProjectOutcome> outcomes,
            Map<Long, ProjectOrders> ordersByProjectId
    ) {
        outcomes.stream()
                .filter(outcome -> outcome.status() == ProjectOutcomeStatus.SUCCEEDED)
                .map(ProjectOutcome::projectId)
                .map(ordersByProjectId::get)
                .filter(projectOrders -> projectOrders.orders().isEmpty()
                        || projectOrders.orders().stream()
                        .noneMatch(order -> order.paymentAmount().amount().signum() > 0))
                .findAny()
                .ifPresent(ignored -> {
                    throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
                });
    }

    private static List<Money> orderPaymentAmounts(ProjectOrders projectOrders) {
        return projectOrders.orders().stream()
                .map(OrderPayment::paymentAmount)
                .toList();
    }

    private static boolean isInvalid(ProjectOutcome outcome) {
        return outcome == null
                || outcome.projectId() == null || outcome.projectId() <= 0
                || outcome.creatorId() == null || outcome.creatorId() <= 0
                || outcome.status() == null;
    }
}
