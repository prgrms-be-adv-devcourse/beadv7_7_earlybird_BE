package com.growmighty.lectures.firstday.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.FinalEffectivePaymentAmountReader;
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
    @DisplayName("Project 결과 중 성공 프로젝트만 정산한다")
    void settlesOnlySucceededProjectOutcomes() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(211L, "seller-211", "********0211"));
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(111L, 211L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(112L, 212L, ProjectOutcomeStatus.FAILED),
                new ProjectOutcome(113L, 213L, ProjectOutcomeStatus.CANCELLED)
        );
        AtomicInteger paymentReads = new AtomicInteger();
        FinalEffectivePaymentAmountReader paymentAmountReader = ignored -> {
            paymentReads.incrementAndGet();
            return List.of(Money.wons(100_000));
        };
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                paymentAmountReader,
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
        Map<Long, List<Money>> amountsByProject = Map.of(
                101L, List.of(Money.wons(100_000)),
                102L, List.of(Money.wons(200_000))
        );
        FinalEffectivePaymentAmountReader paymentAmountReader = amountsByProject::get;
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                paymentAmountReader,
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
        FinalEffectivePaymentAmountReader paymentAmountReader = ignored ->
                paymentReads.getAndIncrement() == 0
                        ? List.of(Money.wons(100_000))
                        : List.of(Money.wons(900_000));
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                paymentAmountReader,
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
