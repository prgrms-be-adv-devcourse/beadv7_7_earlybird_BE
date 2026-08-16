package com.growmighty.lectures.firstday.settlement.application.run;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery.ProjectPayments;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecoveryReader;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementQuery;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import com.growmighty.lectures.firstday.settlement.domain.repository.PgReconciliationRunRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PgReconciliationRunService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SettlementRunInputRepository inputRepository;
    private final OrderPaymentRecoveryReader orderPaymentRecoveryReader;
    private final TossSettlementReader tossSettlementReader;
    private final PgReconciliationRunRepository runRepository;
    private final Clock clock;

    @Transactional(noRollbackFor = SettlementException.class)
    public PgReconciliationRunResult run(YearMonth settlementMonth) {
        PgReconciliationRun running = runRepository.findRunningBySettlementMonth(settlementMonth).orElse(null);
        if (running != null) {
            return resultOf(running, List.of());
        }

        PgReconciliationRun run = runRepository.save(PgReconciliationRun.start(
                settlementMonth,
                LocalDateTime.now(clock)
        ));
        try {
            List<OrderPaymentFact> payments = findCompletedPayments(settlementMonth);
            if (matchesToss(payments, findTossSettlements(settlementMonth))) {
                return complete(run, payments);
            }
            return recoverOrRequireReview(run, payments, settlementMonth);
        } catch (SettlementException exception) {
            run.fail(LocalDateTime.now(clock));
            runRepository.save(run);
            throw exception;
        }
    }

    private PgReconciliationRunResult recoverOrRequireReview(
            PgReconciliationRun run,
            List<OrderPaymentFact> payments,
            YearMonth settlementMonth
    ) {
        boolean recoveredPaymentsMatch = false;
        try {
            OrderPaymentRecovery recovery = orderPaymentRecoveryReader.recover(
                    payments.stream().map(OrderPaymentFact::projectId).collect(java.util.stream.Collectors.toSet()),
                    settlementMonth
            );
            recoveredPaymentsMatch = matchesRecoveredPayments(payments, recovery);
        } catch (IllegalArgumentException exception) {
            // 계약 오류는 재시도로 해소되지 않으므로 운영 검토 대상으로 남긴다.
        }
        List<TossSettlement> recheckedSettlements = findTossSettlements(settlementMonth);
        if (recoveredPaymentsMatch && matchesToss(payments, recheckedSettlements)) {
            return complete(run, payments);
        }
        payments.forEach(OrderPaymentFact::requireReview);
        run.requireReview(LocalDateTime.now(clock));
        return resultOf(runRepository.save(run), List.of());
    }

    private PgReconciliationRunResult complete(PgReconciliationRun run, List<OrderPaymentFact> payments) {
        payments.forEach(OrderPaymentFact::confirmReconciliation);
        run.complete(LocalDateTime.now(clock));
        return resultOf(runRepository.save(run), payments.stream().map(OrderPaymentFact::orderId).toList());
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

    private static boolean matchesRecoveredPayments(
            List<OrderPaymentFact> payments,
            OrderPaymentRecovery recovery
    ) {
        Map<Long, OrderPaymentFact> paymentsByOrderId = new HashMap<>();
        for (OrderPaymentFact payment : payments) {
            if (paymentsByOrderId.put(payment.orderId(), payment) != null) {
                return false;
            }
        }
        List<ProjectPayments> recoveredProjects = recovery.projects();
        if (recoveredProjects.stream().flatMap(project -> project.orders().stream()).count() != payments.size()) {
            return false;
        }
        for (ProjectPayments project : recoveredProjects) {
            for (OrderPayment recovered : project.orders()) {
                OrderPaymentFact payment = paymentsByOrderId.get(recovered.orderId());
                if (payment == null
                        || !payment.projectId().equals(project.projectId())
                        || !payment.pgOrderId().equals(recovered.pgOrderId())
                        || !payment.paymentAmount().equals(recovered.paymentAmount())
                        || recovered.orderStatus() != OrderPayment.Status.PAID) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesToss(List<OrderPaymentFact> payments, List<TossSettlement> settlements) {
        Map<String, Money> paymentAmounts = amountsByOrderId(payments);
        Map<String, Money> settlementAmounts = amountsBySettlementOrderId(settlements);
        return paymentAmounts != null && paymentAmounts.equals(settlementAmounts);
    }

    private static Map<String, Money> amountsByOrderId(List<OrderPaymentFact> payments) {
        Map<String, Money> amounts = new LinkedHashMap<>();
        for (OrderPaymentFact payment : payments) {
            if (amounts.put(payment.pgOrderId(), payment.paymentAmount()) != null) {
                return null;
            }
        }
        return amounts;
    }

    private static Map<String, Money> amountsBySettlementOrderId(List<TossSettlement> settlements) {
        Map<String, Money> amounts = new LinkedHashMap<>();
        for (TossSettlement settlement : settlements) {
            if (amounts.put(settlement.orderId(), settlement.amount()) != null) {
                return null;
            }
        }
        return amounts;
    }

    private static PgReconciliationRunResult resultOf(PgReconciliationRun run, List<Long> confirmedOrderIds) {
        return new PgReconciliationRunResult(run.id(), run.settlementMonth(), run.status(), confirmedOrderIds);
    }
}
