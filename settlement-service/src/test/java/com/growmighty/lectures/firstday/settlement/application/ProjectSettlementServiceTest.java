package com.growmighty.lectures.firstday.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@Transactional
class ProjectSettlementServiceTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Test
    @DisplayName("검증된 프로젝트 입력으로 프로젝트 정산과 지급 의무를 확정한다")
    void confirmsProjectSettlementAndPayoutObligation() {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                10L,
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 7, 23, 9, 0)
        ));
        ConfirmProjectSettlementCommand command = new ConfirmProjectSettlementCommand(
                1L,
                10L,
                List.of(Money.wons(10_015), Money.wons(20_240)),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        ConfirmedProjectSettlement result = projectSettlementService.confirm(command);

        assertThat(result)
                .extracting(
                        ConfirmedProjectSettlement::settlementId,
                        ConfirmedProjectSettlement::payoutObligationId,
                        ConfirmedProjectSettlement::creatorPayoutAmount,
                        ConfirmedProjectSettlement::payoutObligationStatus
                )
                .satisfies(values -> {
                    assertThat(values.get(0)).isNotNull();
                    assertThat(values.get(1)).isNotNull();
                    assertThat(values.get(2)).isEqualTo(Money.wons(27_595));
                    assertThat(values.get(3)).isEqualTo(PayoutObligationStatus.SCHEDULED);
                });
    }

    @Test
    @DisplayName("이미 확정한 프로젝트를 다시 실행하면 기존 결과를 반환한다")
    void returnsExistingResultWhenProjectIsAlreadyConfirmed() {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                20L,
                "seller-20",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********5678",
                LocalDateTime.of(2026, 7, 23, 9, 0)
        ));
        ConfirmProjectSettlementCommand command = new ConfirmProjectSettlementCommand(
                2L,
                20L,
                List.of(Money.wons(100_000)),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );
        ConfirmedProjectSettlement first = projectSettlementService.confirm(command);

        ConfirmedProjectSettlement retried = projectSettlementService.confirm(command);

        assertThat(retried).isEqualTo(first);
    }
}
