// TODO(settlement-plan): Keep tests at the confirmation interface using reconciled payments; remove transport-shaped setup.
package com.growmighty.lectures.firstday.settlement.application.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectSettlementServiceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

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
        ProjectSettlement settlement = projectSettlementRepository.findByProjectId(1L).orElseThrow();

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
        assertThat(settlement.paymentAndSettlementAgencyFeeRate()).isEqualByComparingTo(new BigDecimal("0.04"));
        assertThat(settlement.platformFeeRate()).isEqualByComparingTo(new BigDecimal("0.04"));
        assertThat(settlement.vatRate()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(settlement.scheduledDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(settlement.status()).isEqualTo(PayoutStatus.SCHEDULED);
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
