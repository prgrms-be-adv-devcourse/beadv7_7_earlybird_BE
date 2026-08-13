package com.growmighty.lectures.firstday.settlement.application.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.SettlementRunInputRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementRunServiceTest {

    @Test
    @DisplayName("월별 대사는 모든 토스 정산 페이지를 읽고 일치 결제를 대사 완료로 저장한다")
    void confirmsReconciledPaymentsFromAllTossSettlementPages() {
        List<OrderPaymentFact> payments = IntStream.rangeClosed(1, 5_001)
                .mapToObj(index -> OrderPaymentFact.completed(
                        (long) index,
                        "pg-page-" + index,
                        102L,
                        Money.wons(1),
                        Instant.parse("2026-07-15T10:00:00Z")
                ))
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

        ProjectSettlementRunResult result = new ProjectSettlementRunService(inputs, toss)
                .run(YearMonth.of(2026, 7));

        assertThat(pages).containsExactly(1, 2);
        assertThat(result.confirmedOrderIds()).hasSize(5_001);
        assertThat(payments)
                .extracting(OrderPaymentFact::reconciliationStatus)
                .containsOnly(OrderPaymentFact.ReconciliationStatus.CONFIRMED);
    }
}
