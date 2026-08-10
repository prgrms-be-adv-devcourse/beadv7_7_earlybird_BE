// TODO(settlement-plan): Replace the large synchronous orchestrator with one monthly-run module over stored facts, reconciliation, and batch refund Outbox.
package com.growmighty.lectures.firstday.settlement.application.run;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason.PROJECT_CANCELLED;
import static com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason.PROJECT_FAILED;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.cancellation.PrepareProjectPaymentCancellationCommand;
import com.growmighty.lectures.firstday.settlement.application.cancellation.ProjectPaymentCancellationCommandService;
import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutionResult;
import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutor;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmProjectSettlementCommand;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutSchedulePolicy;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPaymentCancellationCommand;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPaymentCancellationCommandStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ProjectSettlementRunService {

    private final ProjectOutcomeReader projectOutcomeReader;
    private final ProjectOrderReader projectOrderReader;
    private final ProjectPaymentCancellationGateway paymentCancellationGateway;
    private final ProjectSettlementService projectSettlementService;
    private final ProjectPaymentCancellationCommandService cancellationCommandService;
    private final Clock clock;
    private final Optional<PayoutExecutor> payoutExecutor;

    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            ProjectOrderReader projectOrderReader,
            ProjectPaymentCancellationGateway paymentCancellationGateway,
            ProjectSettlementService projectSettlementService,
            ProjectPaymentCancellationCommandService cancellationCommandService,
            Clock clock
    ) {
        this(
                projectOutcomeReader,
                projectOrderReader,
                paymentCancellationGateway,
                projectSettlementService,
                cancellationCommandService,
                clock,
                Optional.empty()
        );
    }

    @Autowired
    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            ProjectOrderReader projectOrderReader,
            ProjectPaymentCancellationGateway paymentCancellationGateway,
            ProjectSettlementService projectSettlementService,
            ProjectPaymentCancellationCommandService cancellationCommandService,
            Clock clock,
            Optional<PayoutExecutor> payoutExecutor
    ) {
        this.projectOutcomeReader = projectOutcomeReader;
        this.projectOrderReader = projectOrderReader;
        this.paymentCancellationGateway = paymentCancellationGateway;
        this.projectSettlementService = projectSettlementService;
        this.cancellationCommandService = cancellationCommandService;
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
        Map<Long, List<ProjectPaymentCancellationCommand>> existingCancellationCommands =
                findExistingCancellationCommands(outcomes);
        List<ProjectOutcome> pendingOutcomes = outcomes.stream()
                .filter(outcome -> !existingSettlements.containsKey(outcome.projectId()))
                .filter(outcome -> !existingCancellationCommands.containsKey(outcome.projectId()))
                .toList();
        Map<Long, ProjectOrders> ordersByProjectId = pendingOutcomes.isEmpty()
                ? Map.of()
                : findProjectOrders(pendingOutcomes);
        List<ProjectPaymentCancellationCommand> newCancellationCommands =
                prepareCancellationCommands(
                pendingOutcomes,
                ordersByProjectId
        );
        List<ProjectOutcome> validatedOutcomes = outcomes;
        List<ProjectPaymentCancellationCommand> cancellationCommands = new ArrayList<>();
        existingCancellationCommands.values().stream()
                .flatMap(List::stream)
                .filter(cancellationCommand -> !existingSettlements.containsKey(
                        cancellationCommand.projectId()
                ))
                .filter(cancellationCommand -> shouldContinueCancellation(
                        cancellationCommand,
                        validatedOutcomes
                ))
                .forEach(cancellationCommands::add);
        cancellationCommands.addAll(newCancellationCommands);
        Map<Long, List<ProjectPaymentCancellationCommand>> cancellationsByProjectId =
                cancelProjectPayments(cancellationCommands).stream()
                        .collect(Collectors.groupingBy(
                                ProjectPaymentCancellationCommand::projectId
                        ));

        List<ConfirmedProjectSettlement> confirmedSettlements = new ArrayList<>();
        List<ProjectOutcomeProcessingResult> projectResults = new ArrayList<>();
        for (ProjectOutcome outcome : outcomes) {
            ConfirmedProjectSettlement existingSettlement = existingSettlements.get(outcome.projectId());
            if (existingSettlement != null) {
                boolean outcomeMatchesSettlement = outcome.status() == ProjectOutcomeStatus.SUCCEEDED
                        && outcome.creatorId().equals(existingSettlement.creatorId())
                        && !existingCancellationCommands.containsKey(outcome.projectId());
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
            List<ProjectPaymentCancellationCommand> projectCancellationCommands =
                    existingCancellationCommands.getOrDefault(outcome.projectId(), List.of());
            if (!projectCancellationCommands.isEmpty()
                    && hasCancellationConflict(outcome, projectCancellationCommands)) {
                projectResults.add(new ProjectOutcomeProcessingResult(
                        outcome.projectId(),
                        outcome.status(),
                        ProjectOutcomeProcessingStatus.OUTCOME_CONFLICT
                ));
                continue;
            }
            if (outcome.status() != ProjectOutcomeStatus.SUCCEEDED) {
                projectResults.add(new ProjectOutcomeProcessingResult(
                        outcome.projectId(),
                        outcome.status(),
                        cancellationProcessingStatus(
                                cancellationsByProjectId.getOrDefault(
                                        outcome.projectId(),
                                        List.of()
                                )
                        )
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

    private Map<Long, List<ProjectPaymentCancellationCommand>> findExistingCancellationCommands(
            List<ProjectOutcome> outcomes
    ) {
        Set<Long> projectIds = outcomes.stream()
                .map(ProjectOutcome::projectId)
                .collect(Collectors.toUnmodifiableSet());
        return cancellationCommandService.findAllByProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(
                        ProjectPaymentCancellationCommand::projectId,
                        Collectors.toUnmodifiableList()
                ));
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

    private List<ProjectPaymentCancellationCommand> prepareCancellationCommands(
            List<ProjectOutcome> outcomes,
            Map<Long, ProjectOrders> ordersByProjectId
    ) {
        List<PrepareProjectPaymentCancellationCommand> commands = outcomes.stream()
                .filter(outcome -> outcome.status() != ProjectOutcomeStatus.SUCCEEDED)
                .flatMap(outcome -> ordersByProjectId.get(outcome.projectId()).orders().stream()
                        .map(OrderPayment::orderId)
                        .map(orderId -> prepareCancellationCommand(outcome, orderId)))
                .toList();
        return cancellationCommandService.prepare(commands);
    }

    private List<ProjectPaymentCancellationCommand> cancelProjectPayments(
            List<ProjectPaymentCancellationCommand> commands
    ) {
        List<ProjectPaymentCancellationCommand> commandsToRequest = commands.stream()
                .filter(command -> command.status().shouldRequestResult())
                .toList();
        List<ProjectPaymentCancellationRequest> requests = commandsToRequest.stream()
                .map(ProjectSettlementRunService::cancellationRequest)
                .toList();
        if (requests.isEmpty()) {
            return commands;
        }
        List<ProjectPaymentCancellationResult> results;
        try {
            results = List.copyOf(paymentCancellationGateway.cancel(requests));
            List<ProjectPaymentCancellationCommand> updated =
                    cancellationCommandService.recordResults(commandsToRequest, results);
            Map<Long, ProjectPaymentCancellationCommand> updatedByOrderId = updated.stream()
                    .collect(Collectors.toMap(
                            ProjectPaymentCancellationCommand::orderId,
                            command -> command
                    ));
            return commands.stream()
                    .map(command -> updatedByOrderId.getOrDefault(command.orderId(), command))
                    .toList();
        } catch (RuntimeException exception) {
            cancellationCommandService.markUnknown(commandsToRequest);
            if (exception instanceof SettlementException settlementException) {
                throw settlementException;
            }
            throw new SettlementException(PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE, exception);
        }
    }

    private static PrepareProjectPaymentCancellationCommand prepareCancellationCommand(
            ProjectOutcome outcome,
            Long orderId
    ) {
        String source = "project-payment-cancellation:"
                + outcome.projectId() + ":" + outcome.status() + ":" + orderId;
        return new PrepareProjectPaymentCancellationCommand(
                outcome.projectId(),
                orderId,
                cancellationReason(outcome.status()),
                UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString()
        );
    }

    private static ProjectPaymentCancellationRequest cancellationRequest(
            ProjectPaymentCancellationCommand command
    ) {
        return new ProjectPaymentCancellationRequest(
                command.orderId(),
                command.reason(),
                command.idempotencyKey()
        );
    }

    private static boolean shouldContinueCancellation(
            ProjectPaymentCancellationCommand command,
            List<ProjectOutcome> outcomes
    ) {
        return outcomes.stream()
                .filter(outcome -> outcome.projectId().equals(command.projectId()))
                .anyMatch(outcome -> outcome.status() != ProjectOutcomeStatus.SUCCEEDED
                        && command.reason() == cancellationReason(outcome.status()));
    }

    private static boolean hasCancellationConflict(
            ProjectOutcome outcome,
            List<ProjectPaymentCancellationCommand> commands
    ) {
        return outcome.status() == ProjectOutcomeStatus.SUCCEEDED
                || commands.stream().anyMatch(
                        command -> command.reason() != cancellationReason(outcome.status())
                );
    }

    private static ProjectCancellationReason cancellationReason(ProjectOutcomeStatus status) {
        return switch (status) {
            case FAILED -> PROJECT_FAILED;
            case CANCELLED -> PROJECT_CANCELLED;
            case SUCCEEDED -> throw new IllegalArgumentException("성공 프로젝트는 결제 취소 사유가 없습니다.");
        };
    }

    private static List<Money> orderPaymentAmounts(ProjectOrders projectOrders) {
        return projectOrders.orders().stream()
                .map(OrderPayment::paymentAmount)
                .toList();
    }

    private static ProjectOutcomeProcessingStatus cancellationProcessingStatus(
            List<ProjectPaymentCancellationCommand> commands
    ) {
        return commands.stream()
                .map(ProjectPaymentCancellationCommand::status)
                .map(ProjectSettlementRunService::processingStatus)
                .max(Comparator.comparingInt(ProjectSettlementRunService::cancellationPriority))
                .orElse(ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED);
    }

    private static ProjectOutcomeProcessingStatus processingStatus(
            ProjectPaymentCancellationCommandStatus status
    ) {
        return switch (status) {
            case COMPLETED, ALREADY_COMPLETED, NO_REFUND_REQUIRED ->
                    ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED;
            case PROCESSING -> ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_PROCESSING;
            case RETRYABLE_FAILED ->
                    ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_RETRYABLE_FAILED;
            case FINAL_FAILED -> ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_FINAL_FAILED;
            case REQUESTED, UNKNOWN -> ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_UNKNOWN;
        };
    }

    private static int cancellationPriority(ProjectOutcomeProcessingStatus status) {
        return switch (status) {
            case PAYMENT_CANCELLATION_COMPLETED -> 0;
            case PAYMENT_CANCELLATION_PROCESSING -> 1;
            case PAYMENT_CANCELLATION_RETRYABLE_FAILED -> 2;
            case PAYMENT_CANCELLATION_FINAL_FAILED -> 3;
            case PAYMENT_CANCELLATION_UNKNOWN -> 4;
            default -> throw new IllegalArgumentException("결제 취소 처리 상태가 아닙니다.");
        };
    }

    private static boolean isInvalid(ProjectOutcome outcome) {
        return outcome == null
                || outcome.projectId() == null || outcome.projectId() <= 0
                || outcome.creatorId() == null || outcome.creatorId() <= 0
                || outcome.status() == null;
    }
}
