package com.growmighty.lectures.firstday.settlement.application.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery.ProjectPayments;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecoveryReader;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import com.growmighty.lectures.firstday.settlement.domain.repository.PgReconciliationRunRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.SettlementRunInputRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PgReconciliationRunServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("월별 대사는 모든 토스 정산 페이지를 읽고 완료 실행 이력과 대사 결과를 저장한다")
    void completesRunAfterReconcilingAllTossSettlementPages() {
        List<OrderPaymentFact> payments = IntStream.rangeClosed(1, 5_001)
                .mapToObj(index -> completedPayment(index))
                .toList();
        SettlementRunInputRepository inputs = (startInclusive, endExclusive) -> payments;
        List<Integer> pages = new ArrayList<>();
        TossSettlementReader toss = query -> {
            pages.add(query.page());
            return payments.stream()
                    .skip((long) (query.page() - 1) * query.size())
                    .limit(query.size())
                    .map(payment -> new TossSettlement(
                            payment.pgOrderId(),
                            "KRW",
                            payment.paymentAmount(),
                            payment.completedAt().atOffset(ZoneOffset.UTC),
                            payment.completedAt().atOffset(ZoneOffset.UTC).toLocalDate()
                    ))
                    .toList();
        };
        InMemoryRunRepository runs = new InMemoryRunRepository();

        PgReconciliationRunResult result = new PgReconciliationRunService(inputs, noRecovery(), toss, runs, CLOCK)
                .run(YearMonth.of(2026, 7));

        assertThat(pages).containsExactly(1, 2);
        assertThat(result.status()).isEqualTo(PgReconciliationRun.Status.COMPLETED);
        assertThat(result.confirmedOrderIds()).hasSize(5_001);
        assertThat(runs.runs()).extracting(PgReconciliationRun::status)
                .containsExactly(PgReconciliationRun.Status.COMPLETED);
        assertThat(payments).extracting(OrderPaymentFact::reconciliationStatus)
                .containsOnly(OrderPaymentFact.ReconciliationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("최초 불일치에서는 Order와 Toss를 각각 한 번 재확인하고 남은 불일치를 검토 대상으로 저장한다")
    void requiresReviewWhenRecoveryDoesNotResolveMismatch() {
        List<OrderPaymentFact> payments = List.of(completedPayment(1));
        InMemoryRunRepository runs = new InMemoryRunRepository();
        AtomicInteger tossReads = new AtomicInteger();
        AtomicInteger orderReads = new AtomicInteger();

        PgReconciliationRunResult result = new PgReconciliationRunService(
                (startInclusive, endExclusive) -> payments,
                (projectIds, settlementMonth) -> {
                    orderReads.incrementAndGet();
                    return recoveryOf(payments, OrderPayment.Status.CANCELLED);
                },
                query -> {
                    return tossReads.incrementAndGet() == 1 ? List.of() : List.of(toSettlement(payments.get(0)));
                },
                runs,
                CLOCK
        ).run(YearMonth.of(2026, 7));

        assertThat(result.status()).isEqualTo(PgReconciliationRun.Status.REVIEW_REQUIRED);
        assertThat(orderReads).hasValue(1);
        assertThat(tossReads).hasValue(2);
        assertThat(runs.runs()).extracting(PgReconciliationRun::status)
                .containsExactly(PgReconciliationRun.Status.REVIEW_REQUIRED);
        assertThat(payments).extracting(OrderPaymentFact::reconciliationStatus)
                .containsOnly(OrderPaymentFact.ReconciliationStatus.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("재확인한 Order와 Toss가 일치하면 결제와 실행을 완료한다")
    void completesRunWhenRecoveryResolvesMismatch() {
        List<OrderPaymentFact> payments = List.of(completedPayment(1));
        InMemoryRunRepository runs = new InMemoryRunRepository();
        AtomicInteger tossReads = new AtomicInteger();

        PgReconciliationRunResult result = new PgReconciliationRunService(
                (startInclusive, endExclusive) -> payments,
                (projectIds, settlementMonth) -> recoveryOf(payments, OrderPayment.Status.PAID),
                query -> tossReads.incrementAndGet() == 1 ? List.of() : List.of(toSettlement(payments.get(0))),
                runs,
                CLOCK
        ).run(YearMonth.of(2026, 7));

        assertThat(result.status()).isEqualTo(PgReconciliationRun.Status.COMPLETED);
        assertThat(tossReads).hasValue(2);
        assertThat(payments).extracting(OrderPaymentFact::reconciliationStatus)
                .containsOnly(OrderPaymentFact.ReconciliationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Order 복구 응답이 계약을 지키지 않으면 재조회 뒤 검토 대상으로 저장한다")
    void requiresReviewWhenRecoveryResponseIsInvalid() {
        List<OrderPaymentFact> payments = List.of(completedPayment(1));
        InMemoryRunRepository runs = new InMemoryRunRepository();
        AtomicInteger tossReads = new AtomicInteger();

        PgReconciliationRunResult result = new PgReconciliationRunService(
                (startInclusive, endExclusive) -> payments,
                (projectIds, settlementMonth) -> {
                    throw new IllegalArgumentException("invalid recovery response");
                },
                query -> tossReads.incrementAndGet() == 1 ? List.of() : List.of(toSettlement(payments.get(0))),
                runs,
                CLOCK
        ).run(YearMonth.of(2026, 7));

        assertThat(result.status()).isEqualTo(PgReconciliationRun.Status.REVIEW_REQUIRED);
        assertThat(tossReads).hasValue(2);
        assertThat(payments).extracting(OrderPaymentFact::reconciliationStatus)
                .containsOnly(OrderPaymentFact.ReconciliationStatus.REVIEW_REQUIRED);
    }

    private static OrderPaymentRecoveryReader noRecovery() {
        return (projectIds, settlementMonth) -> {
            throw new AssertionError("정상 대사에서는 Order 재확인을 호출하면 안 됩니다.");
        };
    }

    private static OrderPaymentRecovery recoveryOf(
            List<OrderPaymentFact> payments,
            OrderPayment.Status orderStatus
    ) {
        return new OrderPaymentRecovery(List.of(new ProjectPayments(
                102L,
                payments.stream().map(payment -> new OrderPayment(
                        payment.orderId(),
                        payment.pgOrderId(),
                        payment.paymentAmount(),
                        orderStatus
                )).toList()
        )));
    }

    private static TossSettlement toSettlement(OrderPaymentFact payment) {
        return new TossSettlement(
                payment.pgOrderId(),
                "KRW",
                payment.paymentAmount(),
                payment.completedAt().atOffset(ZoneOffset.UTC),
                payment.completedAt().atOffset(ZoneOffset.UTC).toLocalDate()
        );
    }

    private static OrderPaymentFact completedPayment(int index) {
        return OrderPaymentFact.completed(
                (long) index,
                "pg-page-" + index,
                102L,
                Money.wons(1),
                Instant.parse("2026-07-15T10:00:00Z")
        );
    }

    private static class InMemoryRunRepository implements PgReconciliationRunRepository {

        private final List<PgReconciliationRun> runs = new ArrayList<>();

        @Override
        public PgReconciliationRun save(PgReconciliationRun run) {
            if (!runs.contains(run)) {
                runs.add(run);
            }
            return run;
        }

        @Override
        public Optional<PgReconciliationRun> findRunningBySettlementMonth(YearMonth settlementMonth) {
            return runs.stream()
                    .filter(PgReconciliationRun::running)
                    .filter(run -> run.settlementMonth().equals(settlementMonth))
                    .findFirst();
        }

        private List<PgReconciliationRun> runs() {
            return List.copyOf(runs);
        }
    }
}
