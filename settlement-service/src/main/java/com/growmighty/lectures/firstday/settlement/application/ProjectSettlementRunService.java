package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectCancellationReason.PROJECT_CANCELLED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectCancellationReason.PROJECT_FAILED;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessment;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessmentReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutSchedulePolicy;
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
    private final PaymentAssessmentReader paymentAssessmentReader;
    private final ProjectPaymentCancellationGateway paymentCancellationGateway;
    private final ProjectSettlementService projectSettlementService;
    private final Clock clock;
    private final Optional<PayoutExecutor> payoutExecutor;

    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            ProjectOrderReader projectOrderReader,
            PaymentAssessmentReader paymentAssessmentReader,
            ProjectPaymentCancellationGateway paymentCancellationGateway,
            ProjectSettlementService projectSettlementService,
            Clock clock
    ) {
        this(
                projectOutcomeReader,
                projectOrderReader,
                paymentAssessmentReader,
                paymentCancellationGateway,
                projectSettlementService,
                clock,
                Optional.empty()
        );
    }

    @Autowired
    public ProjectSettlementRunService(
            ProjectOutcomeReader projectOutcomeReader,
            ProjectOrderReader projectOrderReader,
            PaymentAssessmentReader paymentAssessmentReader,
            ProjectPaymentCancellationGateway paymentCancellationGateway,
            ProjectSettlementService projectSettlementService,
            Clock clock,
            Optional<PayoutExecutor> payoutExecutor
    ) {
        this.projectOutcomeReader = projectOutcomeReader;
        this.projectOrderReader = projectOrderReader;
        this.paymentAssessmentReader = paymentAssessmentReader;
        this.paymentCancellationGateway = paymentCancellationGateway;
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

        Map<Long, ProjectOrders> ordersByProjectId = findProjectOrders(outcomes);
        Set<Long> successfulOrderIds = orderIdsFor(outcomes, ordersByProjectId, ProjectOutcomeStatus.SUCCEEDED);
        Map<Long, PaymentAssessment> assessmentsByOrderId = findPaymentAssessments(successfulOrderIds);
        Map<Long, ProjectPaymentCancellationResult> cancellationsByOrderId = cancelProjectPayments(
                outcomes,
                ordersByProjectId
        );

        List<ConfirmedProjectSettlement> confirmedSettlements = new ArrayList<>();
        List<ProjectOutcomeProcessingResult> projectResults = new ArrayList<>();
        for (ProjectOutcome outcome : outcomes) {
            if (outcome.status() != ProjectOutcomeStatus.SUCCEEDED) {
                projectResults.add(new ProjectOutcomeProcessingResult(
                        outcome.projectId(),
                        outcome.status(),
                        cancellationProcessingStatus(
                                ordersByProjectId.get(outcome.projectId()),
                                cancellationsByOrderId
                        )
                ));
                continue;
            }
            ProjectOrders projectOrders = ordersByProjectId.get(outcome.projectId());
            if (isPaymentNotReady(projectOrders, assessmentsByOrderId)) {
                projectResults.add(new ProjectOutcomeProcessingResult(
                        outcome.projectId(),
                        outcome.status(),
                        ProjectOutcomeProcessingStatus.PAYMENT_NOT_READY
                ));
                continue;
            }
            List<Money> paymentAmounts;
            try {
                paymentAmounts = finalEffectivePaymentAmounts(
                        projectOrders,
                        assessmentsByOrderId
                );
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
            projectResults.add(ProjectOutcomeProcessingResult.settlementConfirmed(outcome.projectId()));
        }

        return new ProjectSettlementRunResult(
                command.settlementMonth(),
                projectResults,
                confirmedSettlements
        );
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
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE, exception);
        }
        Map<Long, ProjectOrders> ordersByProjectId = new HashMap<>();
        Set<Long> allOrderIds = new HashSet<>();
        for (ProjectOrders projectOrders : orderResults) {
            if (projectOrders == null
                    || ordersByProjectId.put(projectOrders.projectId(), projectOrders) != null) {
                throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
            }
            for (Long orderId : projectOrders.orderIds()) {
                if (!allOrderIds.add(orderId)) {
                    throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
                }
            }
        }
        if (!ordersByProjectId.keySet().equals(projectIds)) {
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
        }
        return Map.copyOf(ordersByProjectId);
    }

    private static Set<Long> orderIdsFor(
            List<ProjectOutcome> outcomes,
            Map<Long, ProjectOrders> ordersByProjectId,
            ProjectOutcomeStatus status
    ) {
        Set<Long> orderIds = new HashSet<>();
        outcomes.stream()
                .filter(outcome -> outcome.status() == status)
                .map(ProjectOutcome::projectId)
                .map(ordersByProjectId::get)
                .map(ProjectOrders::orderIds)
                .forEach(orderIds::addAll);
        return Set.copyOf(orderIds);
    }

    private Map<Long, PaymentAssessment> findPaymentAssessments(Set<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<PaymentAssessment> assessments;
        try {
            assessments = List.copyOf(paymentAssessmentReader.findPaymentAssessments(orderIds));
        } catch (SettlementException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE, exception);
        }
        Map<Long, PaymentAssessment> assessmentByOrderId = new HashMap<>();
        for (PaymentAssessment assessment : assessments) {
            if (assessment == null || assessmentByOrderId.put(assessment.orderId(), assessment) != null) {
                throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
            }
        }
        if (!new HashSet<>(assessmentByOrderId.keySet()).equals(orderIds)) {
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
        }
        return Map.copyOf(assessmentByOrderId);
    }

    private Map<Long, ProjectPaymentCancellationResult> cancelProjectPayments(
            List<ProjectOutcome> outcomes,
            Map<Long, ProjectOrders> ordersByProjectId
    ) {
        List<ProjectPaymentCancellationRequest> requests = outcomes.stream()
                .filter(outcome -> outcome.status() != ProjectOutcomeStatus.SUCCEEDED)
                .flatMap(outcome -> ordersByProjectId.get(outcome.projectId()).orderIds().stream()
                        .map(orderId -> cancellationRequest(outcome, orderId)))
                .toList();
        if (requests.isEmpty()) {
            return Map.of();
        }
        List<ProjectPaymentCancellationResult> results;
        try {
            results = List.copyOf(paymentCancellationGateway.cancel(requests));
        } catch (SettlementException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE, exception);
        }
        Set<Long> requestedOrderIds = requests.stream()
                .map(ProjectPaymentCancellationRequest::orderId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, ProjectPaymentCancellationResult> resultByOrderId = new HashMap<>();
        for (ProjectPaymentCancellationResult result : results) {
            if (result == null || resultByOrderId.put(result.orderId(), result) != null) {
                throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
            }
        }
        if (!resultByOrderId.keySet().equals(requestedOrderIds)) {
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE);
        }
        return Map.copyOf(resultByOrderId);
    }

    private static ProjectPaymentCancellationRequest cancellationRequest(
            ProjectOutcome outcome,
            Long orderId
    ) {
        String source = "project-payment-cancellation:"
                + outcome.projectId() + ":" + outcome.status() + ":" + orderId;
        return new ProjectPaymentCancellationRequest(
                orderId,
                outcome.status() == ProjectOutcomeStatus.FAILED ? PROJECT_FAILED : PROJECT_CANCELLED,
                UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString()
        );
    }

    private static List<Money> finalEffectivePaymentAmounts(
            ProjectOrders projectOrders,
            Map<Long, PaymentAssessment> assessmentByOrderId
    ) {
        return projectOrders.orderIds().stream()
                .map(assessmentByOrderId::get)
                .map(ProjectSettlementRunService::finalEffectiveAmount)
                .toList();
    }

    private static boolean isPaymentNotReady(
            ProjectOrders projectOrders,
            Map<Long, PaymentAssessment> assessmentByOrderId
    ) {
        return projectOrders.orderIds().stream()
                .map(assessmentByOrderId::get)
                .anyMatch(PaymentAssessment.NotReady.class::isInstance);
    }

    private static ProjectOutcomeProcessingStatus cancellationProcessingStatus(
            ProjectOrders projectOrders,
            Map<Long, ProjectPaymentCancellationResult> cancellationsByOrderId
    ) {
        if (projectOrders.orderIds().isEmpty()) {
            return ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED;
        }
        return projectOrders.orderIds().stream()
                .map(cancellationsByOrderId::get)
                .map(ProjectPaymentCancellationResult::status)
                .map(ProjectSettlementRunService::processingStatus)
                .max(Comparator.comparingInt(ProjectSettlementRunService::cancellationPriority))
                .orElse(ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED);
    }

    private static ProjectOutcomeProcessingStatus processingStatus(
            ProjectPaymentCancellationStatus status
    ) {
        return switch (status) {
            case COMPLETED, ALREADY_COMPLETED, NO_REFUND_REQUIRED ->
                    ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED;
            case PROCESSING -> ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_PROCESSING;
            case RETRYABLE_FAILED ->
                    ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_RETRYABLE_FAILED;
            case FINAL_FAILED -> ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_FINAL_FAILED;
            case UNKNOWN -> ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_UNKNOWN;
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
