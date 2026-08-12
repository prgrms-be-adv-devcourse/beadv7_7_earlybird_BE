package com.growmighty.lectures.firstday.settlement.application.run;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutionResult;
import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutor;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementQuery;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmProjectSettlementCommand;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutSchedulePolicy;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.SettlementRunInputRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class ProjectSettlementRunService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SettlementRunInputRepository inputRepository;
    private final TossSettlementReader tossSettlementReader;
    private final ProjectSettlementService projectSettlementService;
    private final Clock clock;
    private final Optional<PayoutExecutor> payoutExecutor;

    public ProjectSettlementRunService(
            SettlementRunInputRepository inputRepository,
            TossSettlementReader tossSettlementReader,
            ProjectSettlementService projectSettlementService,
            Clock clock
    ) {
        this(
                inputRepository,
                tossSettlementReader,
                projectSettlementService,
                clock,
                Optional.empty()
        );
    }

    @Autowired
    public ProjectSettlementRunService(
            SettlementRunInputRepository inputRepository,
            TossSettlementReader tossSettlementReader,
            ProjectSettlementService projectSettlementService,
            Clock clock,
            Optional<PayoutExecutor> payoutExecutor
    ) {
        this.inputRepository = inputRepository;
        this.tossSettlementReader = tossSettlementReader;
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
        List<ProjectOutcomeFact> outcomes = findProjectOutcomes();
        if (outcomes.isEmpty()) {
            return new ProjectSettlementRunResult(command.settlementMonth(), List.of(), List.of());
        }
        List<OrderPaymentFact> payments = findCompletedPayments(command.settlementMonth());
        reconcile(payments, findTossSettlements(command.settlementMonth()));

        Map<Long, ConfirmedProjectSettlement> existingSettlements = findExistingSettlements(outcomes);
        Map<Long, List<Money>> paymentAmountsByProject = paymentAmountsByProject(payments);
        List<ConfirmedProjectSettlement> confirmedSettlements = new ArrayList<>();
        List<ProjectOutcomeProcessingResult> projectResults = new ArrayList<>();
        for (ProjectOutcomeFact outcome : outcomes) {
            ConfirmedProjectSettlement existingSettlement = existingSettlements.get(outcome.projectId());
            if (existingSettlement != null) {
                boolean outcomeMatchesSettlement = outcome.requiresPayout()
                        && outcome.creatorId().equals(existingSettlement.creatorId());
                ConfirmedProjectSettlement restored = outcomeMatchesSettlement
                        ? executePayout(existingSettlement)
                        : existingSettlement;
                confirmedSettlements.add(restored);
                projectResults.add(new ProjectOutcomeProcessingResult(
                        outcome.projectId(),
                        statusOf(outcome),
                        outcomeMatchesSettlement
                                ? ProjectOutcomeProcessingStatus.SETTLEMENT_ALREADY_CONFIRMED
                                : ProjectOutcomeProcessingStatus.OUTCOME_CONFLICT
                ));
                continue;
            }
            if (!outcome.requiresPayout()) {
                projectResults.add(new ProjectOutcomeProcessingResult(
                        outcome.projectId(),
                        statusOf(outcome),
                        ProjectOutcomeProcessingStatus.REFUND_REQUEST_PENDING
                ));
                continue;
            }
            List<Money> paymentAmounts = paymentAmountsByProject.getOrDefault(outcome.projectId(), List.of());
            if (paymentAmounts.isEmpty()) {
                throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
            }
            ConfirmedProjectSettlement confirmed = projectSettlementService.confirm(
                    new ConfirmProjectSettlementCommand(
                            outcome.projectId(),
                            outcome.creatorId(),
                            paymentAmounts,
                            command.scheduledDate(),
                            command.confirmedAt()
                    )
            );
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

    private List<ProjectOutcomeFact> findProjectOutcomes() {
        try {
            List<ProjectOutcomeFact> outcomes = List.copyOf(inputRepository.findProjectOutcomes());
            if (outcomes.stream().anyMatch(outcome -> outcome == null)) {
                throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
            }
            return outcomes;
        } catch (SettlementException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE, exception);
        }
    }

    private List<OrderPaymentFact> findCompletedPayments(YearMonth settlementMonth) {
        try {
            LocalDate startDate = settlementMonth.atDay(1);
            Instant startInclusive = startDate.atStartOfDay(SEOUL).toInstant();
            Instant endExclusive = settlementMonth.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toInstant();
            List<OrderPaymentFact> payments = List.copyOf(
                    inputRepository.findCompletedPayments(startInclusive, endExclusive)
            );
            if (payments.stream().anyMatch(payment -> payment == null)) {
                throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
            }
            return payments;
        } catch (SettlementException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }
    }

    private List<TossSettlement> findTossSettlements(YearMonth settlementMonth) {
        try {
            LocalDate startDate = settlementMonth.atDay(1);
            LocalDate endDate = settlementMonth.atEndOfMonth();
            List<TossSettlement> settlements = new ArrayList<>();
            for (int page = 1; ; page++) {
                List<TossSettlement> currentPage = List.copyOf(tossSettlementReader.find(
                        new TossSettlementQuery(
                                startDate,
                                endDate,
                                TossSettlementQuery.DateType.SOLD_DATE,
                                page,
                                TossSettlementQuery.MAX_SIZE
                        )
                ));
                settlements.addAll(currentPage);
                if (currentPage.size() < TossSettlementQuery.MAX_SIZE) {
                    return List.copyOf(settlements);
                }
            }
        } catch (RuntimeException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }
    }

    private static void reconcile(
            List<OrderPaymentFact> payments,
            List<TossSettlement> settlements
    ) {
        if (!amountsByOrderId(payments).equals(amountsBySettlementOrderId(settlements))) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
        }
    }

    private static Map<String, Money> amountsByOrderId(List<OrderPaymentFact> payments) {
        Map<String, Money> amounts = new LinkedHashMap<>();
        for (OrderPaymentFact payment : payments) {
            if (amounts.put(payment.pgOrderId(), payment.paymentAmount()) != null) {
                throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
            }
        }
        return amounts;
    }

    private static Map<String, Money> amountsBySettlementOrderId(List<TossSettlement> settlements) {
        Map<String, Money> amounts = new LinkedHashMap<>();
        for (TossSettlement settlement : settlements) {
            if (amounts.put(settlement.orderId(), settlement.amount()) != null) {
                throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
            }
        }
        return amounts;
    }

    private static Map<Long, List<Money>> paymentAmountsByProject(
            List<OrderPaymentFact> payments
    ) {
        Map<Long, List<Money>> amountsByProject = new HashMap<>();
        for (OrderPaymentFact payment : payments) {
            amountsByProject.computeIfAbsent(payment.projectId(), ignored -> new ArrayList<>())
                    .add(payment.paymentAmount());
        }
        return Map.copyOf(amountsByProject);
    }

    private Map<Long, ConfirmedProjectSettlement> findExistingSettlements(
            List<ProjectOutcomeFact> outcomes
    ) {
        Map<Long, ConfirmedProjectSettlement> existingSettlements = new HashMap<>();
        for (ProjectOutcomeFact outcome : outcomes) {
            projectSettlementService.findConfirmedByProjectId(outcome.projectId())
                    .ifPresent(settlement -> existingSettlements.put(outcome.projectId(), settlement));
        }
        return Map.copyOf(existingSettlements);
    }

    private ConfirmedProjectSettlement executePayout(ConfirmedProjectSettlement settlement) {
        if (payoutExecutor.isEmpty()) {
            return settlement;
        }
        PayoutExecutionResult payoutResult = payoutExecutor.get().execute(settlement.settlementId());
        return settlement.withPayoutStatus(payoutResult.payoutStatus());
    }

    private static ProjectOutcomeStatus statusOf(ProjectOutcomeFact outcome) {
        return ProjectOutcomeStatus.valueOf(outcome.outcome().name());
    }
}
