package com.growmighty.lectures.firstday.settlement.application.run;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.SettlementRunInputRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProjectSettlementRunServiceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Test
    @DisplayName("저장된 결제 사실과 토스 정산 내역이 일치하면 성공 프로젝트를 정산한다")
    void settlesSucceededProjectFromReconciledPaymentFacts() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(510L));
        OrderPaymentFact first = payment(5_101L, "pg-510-1", 510L, 40_000);
        OrderPaymentFact second = payment(5_102L, "pg-510-2", 510L, 60_000);

        ProjectSettlementRunResult result = service(
                List.of(succeeded(510L)),
                List.of(first, second)
        ).run(command());

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("실패·취소 프로젝트는 결제 사실이 없어도 환불 요청 대기로만 분류한다")
    void leavesRefundProjectsToOutboxFlow() {
        ProjectSettlementRunResult result = service(
                List.of(outcome(520L, ProjectOutcomeFact.Outcome.FAILED), outcome(521L, ProjectOutcomeFact.Outcome.CANCELLED)),
                List.of()
        ).run(command());

        assertThat(result.projectResults())
                .extracting(ProjectOutcomeProcessingResult::projectId, ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(
                        tuple(520L, ProjectOutcomeProcessingStatus.REFUND_REQUEST_PENDING),
                        tuple(521L, ProjectOutcomeProcessingStatus.REFUND_REQUEST_PENDING)
                );
    }

    @Test
    @DisplayName("성공 프로젝트에 대응하는 완료 결제 사실이 없으면 실행을 거부한다")
    void rejectsMissingPaymentFacts() {
        assertThatThrownBy(() -> service(List.of(succeeded(530L)), List.of()).run(command()))
                .isInstanceOfSatisfying(
                        SettlementException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ORDER_PAYMENT_INPUTS_UNAVAILABLE)
                );
    }

    @Test
    @DisplayName("이미 확정된 성공 프로젝트는 저장된 사실을 다시 대사해도 기존 정산을 복원한다")
    void restoresExistingSettlement() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(540L));
        OrderPaymentFact payment = payment(5_401L, "pg-540", 540L, 100_000);
        service(List.of(succeeded(540L)), List.of(payment)).run(command());

        ProjectSettlementRunResult result = service(List.of(succeeded(540L)), List.of(payment)).run(command());

        assertThat(result.projectResults().getFirst().processingStatus())
                .isEqualTo(ProjectOutcomeProcessingStatus.SETTLEMENT_ALREADY_CONFIRMED);
    }

    private ProjectSettlementRunService service(List<ProjectOutcomeFact> outcomes, List<OrderPaymentFact> payments) {
        SettlementRunInputRepository inputs = new SettlementRunInputRepository() {
            @Override
            public List<ProjectOutcomeFact> findProjectOutcomes() {
                return outcomes;
            }

            @Override
            public List<OrderPaymentFact> findCompletedPayments(Instant startInclusive, Instant endExclusive) {
                return payments;
            }
        };
        TossSettlementReader toss = query -> payments.stream()
                .map(payment -> new TossSettlement(
                        payment.pgOrderId(),
                        "KRW",
                        payment.paymentAmount(),
                        payment.completedAt().atOffset(ZoneOffset.UTC),
                        payment.completedAt().atOffset(ZoneOffset.UTC).toLocalDate()
                ))
                .toList();
        return new ProjectSettlementRunService(inputs, toss, projectSettlementService, fixedClock());
    }

    private static ProjectOutcomeFact succeeded(Long projectId) {
        return outcome(projectId, ProjectOutcomeFact.Outcome.SUCCEEDED);
    }

    private static ProjectOutcomeFact outcome(Long projectId, ProjectOutcomeFact.Outcome outcome) {
        return ProjectOutcomeFact.of(projectId, projectId, outcome, Instant.parse("2026-07-23T10:00:00Z"));
    }

    private static OrderPaymentFact payment(Long orderId, String pgOrderId, Long projectId, long amount) {
        return OrderPaymentFact.completed(
                orderId, pgOrderId, projectId, Money.wons(amount), Instant.parse("2026-07-15T10:00:00Z")
        );
    }

    private static RunProjectSettlementsCommand command() {
        return new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7), LocalDate.of(2026, 8, 3), LocalDateTime.of(2026, 7, 23, 10, 0)
        );
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
    }

    private static CreatorPayoutProfile payoutReadyProfile(Long creatorId) {
        return CreatorPayoutProfile.registered(
                creatorId, "seller-" + creatorId, CreatorPayoutStatus.PAYOUT_READY, "088", "********" + creatorId,
                LocalDateTime.of(2026, 7, 23, 9, 0)
        );
    }
}
