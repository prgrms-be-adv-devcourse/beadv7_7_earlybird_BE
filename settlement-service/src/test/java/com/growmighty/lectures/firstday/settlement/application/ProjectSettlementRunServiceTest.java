package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessment;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessmentReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProjectSettlementRunServiceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private ProjectSettlementRunService connectedRunService;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Test
    @DisplayName("Order의 주문 식별자와 Payment의 결제 판정으로 성공 프로젝트를 정산한다")
    void settlesSucceededProjectFromOrderAndPaymentInputs() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(210L, "seller-210", "********0210"));
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(110L, 210L, ProjectOutcomeStatus.SUCCEEDED)
        );
        ProjectOrderReader orderReader = projectIds -> List.of(
                new ProjectOrders(110L, List.of(1_001L, 1_002L))
        );
        PaymentAssessmentReader paymentReader = orderIds -> List.of(
                PaymentAssessment.ready(1_001L, Money.wons(40_000)),
                PaymentAssessment.ready(1_002L, Money.wons(60_000))
        );
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                projectSettlementService,
                Clock.fixed(
                        LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                        ZoneOffset.UTC
                )
        );

        ProjectSettlementRunResult result = runService.run(new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        ));

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("결제가 없는 주문은 0원 항목으로 보존하고 준비된 결제만 정산 금액에 반영한다")
    void treatsNoPaymentAsZeroAmountItem() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(214L, "seller-214", "********0214"));
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(114L, 214L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(new ProjectOrders(114L, List.of(1_003L, 1_004L))),
                orderIds -> List.of(
                        PaymentAssessment.noPayment(1_003L),
                        PaymentAssessment.ready(1_004L, Money.wons(100_000))
                ),
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("준비되지 않은 결제 판정이 있으면 프로젝트 정산을 확정하지 않는다")
    void rejectsNotReadyPaymentAssessment() {
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(115L, 215L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(new ProjectOrders(115L, List.of(1_005L))),
                orderIds -> List.of(PaymentAssessment.notReady(1_005L)),
                projectSettlementService,
                fixedClock()
        );

        assertThatThrownBy(() -> runService.run(command()))
                .isInstanceOfSatisfying(SettlementException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE)
                );
    }

    @Test
    @DisplayName("Project 결과 중 성공 프로젝트만 정산한다")
    void settlesOnlySucceededProjectOutcomes() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(211L, "seller-211", "********0211"));
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(111L, 211L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(112L, 212L, ProjectOutcomeStatus.FAILED),
                new ProjectOutcome(113L, 213L, ProjectOutcomeStatus.CANCELLED)
        );
        AtomicInteger paymentReads = new AtomicInteger();
        ProjectOrderReader orderReader = sameIdProjectOrderReader();
        PaymentAssessmentReader paymentReader = orderIds -> {
            paymentReads.incrementAndGet();
            return orderIds.stream()
                    .map(orderId -> PaymentAssessment.ready(orderId, Money.wons(100_000)))
                    .toList();
        };
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                projectSettlementService,
                Clock.fixed(
                        LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                        ZoneOffset.UTC
                )
        );

        ProjectSettlementRunResult result = runService.run(new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        ));

        assertThat(paymentReads).hasValue(1);
        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::projectId)
                .containsExactly(111L);
    }

    @Test
    @DisplayName("대상 월의 모든 프로젝트 정산을 실행한다")
    void runsAllProjectSettlementsForMonth() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(201L, "seller-201", "********0201"));
        creatorPayoutProfileRepository.save(payoutReadyProfile(202L, "seller-202", "********0202"));
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(101L, 201L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(102L, 202L, ProjectOutcomeStatus.SUCCEEDED)
        );
        Map<Long, Money> amountsByOrder = Map.of(
                101L, Money.wons(100_000),
                102L, Money.wons(200_000)
        );
        ProjectOrderReader orderReader = sameIdProjectOrderReader();
        PaymentAssessmentReader paymentReader = orderIds -> orderIds.stream()
                .map(orderId -> PaymentAssessment.ready(orderId, amountsByOrder.get(orderId)))
                .toList();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                projectSettlementService,
                Clock.fixed(
                        LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                        ZoneOffset.UTC
                )
        );
        RunProjectSettlementsCommand command = new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        ProjectSettlementRunResult result = runService.run(command);

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200), Money.wons(182_400));
    }

    @Test
    @DisplayName("더미 외부 정보로 프로젝트 정산을 실행한다")
    void runsWithDummyExternalInformation() {
        RunProjectSettlementsCommand command = new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        ProjectSettlementRunResult result = connectedRunService.run(command);

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("같은 월에 같은 프로젝트를 다시 조회해도 기존 프로젝트 정산을 유지한다")
    void keepsExistingSettlementWhenMonthRunIsRepeated() {
        long projectId = 103L;
        long creatorId = 203L;
        creatorPayoutProfileRepository.save(
                payoutReadyProfile(creatorId, "seller-203", "********0203")
        );
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(projectId, creatorId, ProjectOutcomeStatus.SUCCEEDED)
        );
        AtomicInteger paymentReads = new AtomicInteger();
        ProjectOrderReader orderReader = sameIdProjectOrderReader();
        PaymentAssessmentReader paymentReader = orderIds -> {
            Money amount = paymentReads.getAndIncrement() == 0
                    ? Money.wons(100_000)
                    : Money.wons(900_000);
            return orderIds.stream()
                    .map(orderId -> PaymentAssessment.ready(orderId, amount))
                    .toList();
        };
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                projectSettlementService,
                Clock.fixed(
                        LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                        ZoneOffset.UTC
                )
        );
        RunProjectSettlementsCommand command = new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        ProjectSettlementRunResult first = runService.run(command);
        ProjectSettlementRunResult second = runService.run(command);

        assertThat(paymentReads).hasValue(2);
        assertThat(first.confirmedSettlements())
                .containsExactlyElementsOf(second.confirmedSettlements());
        assertThat(second.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    private static ProjectOrderReader sameIdProjectOrderReader() {
        return projectIds -> projectIds.stream()
                .map(projectId -> new ProjectOrders(projectId, List.of(projectId)))
                .toList();
    }

    private static RunProjectSettlementsCommand command() {
        return new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );
    }

    private static Clock fixedClock() {
        return Clock.fixed(
                LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
    }

    private static CreatorPayoutProfile payoutReadyProfile(
            Long creatorId,
            String sellerId,
            String maskedAccountNumber
    ) {
        return CreatorPayoutProfile.registered(
                creatorId,
                sellerId,
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                maskedAccountNumber,
                LocalDateTime.of(2026, 7, 23, 9, 0)
        );
    }
}
