package com.growmighty.lectures.firstday.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessment;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessmentReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementRunPayoutTest {

    @Test
    @DisplayName("지급 연동이 활성화되면 확정된 지급 의무의 지급을 실행한다")
    void executesPayoutForConfirmedSettlement() {
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(101L, 201L, ProjectOutcomeStatus.SUCCEEDED)
        );
        ProjectOrderReader orderReader = projectIds -> List.of(
                new ProjectOrders(101L, List.of(1_001L))
        );
        PaymentAssessmentReader paymentReader = orderIds -> List.of(
                PaymentAssessment.ready(1_001L, Money.wons(100_000))
        );
        ProjectSettlementService settlementService = mock(ProjectSettlementService.class);
        when(settlementService.confirm(any())).thenReturn(new ConfirmedProjectSettlement(
                101L,
                201L,
                301L,
                401L,
                Money.wons(91_200),
                PayoutObligationStatus.SCHEDULED,
                LocalDate.of(2026, 8, 3)
        ));
        AtomicReference<Long> requestedObligationId = new AtomicReference<>();
        PayoutExecutor payoutExecutor = obligationId -> {
            requestedObligationId.set(obligationId);
            return new PayoutExecutionResult(
                    obligationId,
                    1,
                    PayoutAttemptStatus.REQUESTED,
                    PayoutObligationStatus.PROCESSING
            );
        };
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                settlementService,
                Clock.fixed(Instant.parse("2026-07-26T01:00:00Z"), ZoneOffset.UTC),
                Optional.of(payoutExecutor)
        );

        ProjectSettlementRunResult result = runService.run(new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 26, 1, 0)
        ));

        assertThat(requestedObligationId.get()).isEqualTo(401L);
        assertThat(result.confirmedSettlements().getFirst().payoutObligationStatus())
                .isEqualTo(PayoutObligationStatus.PROCESSING);
    }
}
