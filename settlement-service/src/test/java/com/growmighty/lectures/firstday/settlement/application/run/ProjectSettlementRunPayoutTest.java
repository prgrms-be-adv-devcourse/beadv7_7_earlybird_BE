package com.growmighty.lectures.firstday.settlement.application.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutionResult;
import com.growmighty.lectures.firstday.settlement.application.payout.PayoutExecutor;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.SettlementRunInputRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementRunPayoutTest {

    @Test
    @DisplayName("월 실행은 토스 정산 조회의 모든 페이지를 대사한다")
    void reconcilesAllTossSettlementPages() {
        List<OrderPaymentFact> payments = IntStream.rangeClosed(1, 5_001)
                .mapToObj(index -> OrderPaymentFact.completed(
                        (long) index,
                        "pg-page-" + index,
                        102L,
                        Money.wons(1),
                        Instant.parse("2026-07-15T10:00:00Z")
                ))
                .toList();
        SettlementRunInputRepository inputs = new SettlementRunInputRepository() {
            @Override
            public List<ProjectOutcomeFact> findProjectOutcomes() {
                return List.of(ProjectOutcomeFact.of(
                        102L, 202L, ProjectOutcomeFact.Outcome.SUCCEEDED, Instant.parse("2026-07-20T10:00:00Z")
                ));
            }

            @Override
            public List<OrderPaymentFact> findCompletedPayments(Instant startInclusive, Instant endExclusive) {
                return payments;
            }
        };
        List<Integer> pages = new java.util.ArrayList<>();
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
        ProjectSettlementService settlementService = mock(ProjectSettlementService.class);
        when(settlementService.confirm(any())).thenReturn(new ConfirmedProjectSettlement(
                102L, 202L, 302L, Money.wons(4_560), PayoutStatus.SCHEDULED, LocalDate.of(2026, 8, 3)
        ));

        new ProjectSettlementRunService(
                inputs, toss, settlementService, Clock.fixed(Instant.parse("2026-07-26T01:00:00Z"), ZoneOffset.UTC)
        ).run(new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7), LocalDate.of(2026, 8, 3), LocalDateTime.of(2026, 7, 26, 1, 0)
        ));

        assertThat(pages).containsExactly(1, 2);
    }

    @Test
    @DisplayName("성공 정산은 프로젝트 정산 식별자로 지급 흐름을 실행한다")
    void executesPayoutForConfirmedSettlement() {
        OrderPaymentFact payment = OrderPaymentFact.completed(
                1_001L, "pg-1001", 101L, Money.wons(100_000), Instant.parse("2026-07-15T10:00:00Z")
        );
        SettlementRunInputRepository inputs = new SettlementRunInputRepository() {
            @Override
            public List<ProjectOutcomeFact> findProjectOutcomes() {
                return List.of(ProjectOutcomeFact.of(
                        101L, 201L, ProjectOutcomeFact.Outcome.SUCCEEDED, Instant.parse("2026-07-20T10:00:00Z")
                ));
            }

            @Override
            public List<OrderPaymentFact> findCompletedPayments(Instant startInclusive, Instant endExclusive) {
                return List.of(payment);
            }
        };
        TossSettlementReader toss = query -> List.of(new TossSettlement(
                payment.pgOrderId(), "KRW", payment.paymentAmount(), payment.completedAt().atOffset(ZoneOffset.UTC),
                payment.completedAt().atOffset(ZoneOffset.UTC).toLocalDate()
        ));
        ProjectSettlementService settlementService = mock(ProjectSettlementService.class);
        when(settlementService.confirm(any())).thenReturn(new ConfirmedProjectSettlement(
                101L, 201L, 301L, Money.wons(91_200), PayoutStatus.SCHEDULED, LocalDate.of(2026, 8, 3)
        ));
        AtomicReference<Long> requestedSettlementId = new AtomicReference<>();
        PayoutExecutor payoutExecutor = settlementId -> {
            requestedSettlementId.set(settlementId);
            return new PayoutExecutionResult(settlementId, 1, PayoutAttemptStatus.REQUESTED, PayoutStatus.PROCESSING);
        };
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                inputs, toss, settlementService, Clock.fixed(Instant.parse("2026-07-26T01:00:00Z"), ZoneOffset.UTC),
                Optional.of(payoutExecutor)
        );

        ProjectSettlementRunResult result = runService.run(new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7), LocalDate.of(2026, 8, 3), LocalDateTime.of(2026, 7, 26, 1, 0)
        ));

        assertThat(requestedSettlementId.get()).isEqualTo(301L);
        assertThat(result.confirmedSettlements().getFirst().payoutStatus()).isEqualTo(PayoutStatus.PROCESSING);
    }
}
