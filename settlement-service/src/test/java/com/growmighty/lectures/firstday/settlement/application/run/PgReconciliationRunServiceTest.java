package com.growmighty.lectures.firstday.settlement.application.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
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

        PgReconciliationRunResult result = new PgReconciliationRunService(inputs, toss, runs, CLOCK)
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
    @DisplayName("대사가 실패하면 실패 실행 이력을 저장하고 결제 상태를 변경하지 않는다")
    void savesFailedRunWhenReconciliationFails() {
        List<OrderPaymentFact> payments = List.of(completedPayment(1));
        InMemoryRunRepository runs = new InMemoryRunRepository();

        assertThatThrownBy(() -> new PgReconciliationRunService(
                (startInclusive, endExclusive) -> payments,
                query -> List.of(),
                runs,
                CLOCK
        ).run(YearMonth.of(2026, 7))).isInstanceOf(SettlementException.class);

        assertThat(runs.runs()).extracting(PgReconciliationRun::status)
                .containsExactly(PgReconciliationRun.Status.FAILED);
        assertThat(payments).extracting(OrderPaymentFact::reconciliationStatus)
                .containsOnly(OrderPaymentFact.ReconciliationStatus.PENDING);
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
