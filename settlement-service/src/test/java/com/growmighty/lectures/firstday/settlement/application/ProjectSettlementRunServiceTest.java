package com.growmighty.lectures.firstday.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.FinalEffectivePaymentAmountReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProjectSettlementRunServiceTest {

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private ProjectSettlementRunService connectedRunService;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Test
    @DisplayName("대상 월의 모든 프로젝트 정산을 실행한다")
    void runsAllProjectSettlementsForMonth() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(201L, "seller-201", "********0201"));
        creatorPayoutProfileRepository.save(payoutReadyProfile(202L, "seller-202", "********0202"));
        ProjectSettlementTargetReader targetReader = month -> List.of(
                new ProjectSettlementTarget(101L, "첫 번째 프로젝트", 201L),
                new ProjectSettlementTarget(102L, "두 번째 프로젝트", 202L)
        );
        Map<Long, List<Money>> amountsByProject = Map.of(
                101L, List.of(Money.wons(100_000)),
                102L, List.of(Money.wons(200_000))
        );
        FinalEffectivePaymentAmountReader paymentAmountReader = amountsByProject::get;
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                targetReader,
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
