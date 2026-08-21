// TODO(settlement-plan): Keep tests at the confirmation interface using reconciled payments; remove transport-shaped setup.
package com.growmighty.lectures.firstday.settlement.application.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
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

    @Autowired
    private PayoutObligationRepository payoutObligationRepository;

    @Test
    @DisplayName("검증된 프로젝트 입력으로 프로젝트 정산과 최초 지급 상태를 확정한다")
    void confirmsProjectSettlementAndInitialPayoutState() {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                10L,
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY
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
        PayoutObligation payoutObligation = payoutObligationRepository.findBySettlementId(settlement.id()).orElseThrow();

        assertThat(result)
                .extracting(
                        ConfirmedProjectSettlement::settlementId,
                        ConfirmedProjectSettlement::creatorPayoutAmount
                )
                .satisfies(values -> {
                    assertThat(values.get(0)).isNotNull();
                    assertThat(values.get(1)).isEqualTo(Money.wons(27_595));
                });
        assertThat(result.payoutStatus()).contains(PayoutStatus.SCHEDULED);
        assertThat(settlement.paymentAndSettlementAgencyFeeRate()).isEqualByComparingTo(new BigDecimal("0.04"));
        assertThat(settlement.platformFeeRate()).isEqualByComparingTo(new BigDecimal("0.04"));
        assertThat(settlement.vatRate()).isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(payoutObligation.scheduledDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(payoutObligation.status()).isEqualTo(PayoutStatus.SCHEDULED);
    }

    @Test
    @DisplayName("이미 확정한 프로젝트를 다시 실행하면 기존 결과를 반환한다")
    void returnsExistingResultWhenProjectIsAlreadyConfirmed() {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                20L,
                "seller-20",
                CreatorPayoutStatus.PAYOUT_READY
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

    @Test
    @DisplayName("지급 프로필이 없으면 등록 대기 프로필과 프로젝트 정산만 확정한다")
    void confirmsSettlementAndDefersPayoutWhenProfileIsMissing() {
        ConfirmProjectSettlementCommand command = new ConfirmProjectSettlementCommand(
                3L,
                30L,
                List.of(Money.wons(100_000)),
                LocalDate.of(2026, 8, 5),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        ConfirmedProjectSettlement result = projectSettlementService.confirm(command);

        assertThat(creatorPayoutProfileRepository.findByCreatorId(30L).orElseThrow().status())
                .isEqualTo(CreatorPayoutStatus.REGISTRATION_PENDING);
        assertThat(projectSettlementRepository.findByProjectId(3L)).isPresent();
        assertThat(payoutObligationRepository.findBySettlementId(result.settlementId())).isEmpty();
        assertThat(result.hasPayoutObligation()).isFalse();
        assertThat(result.payoutStatus()).isEmpty();
    }
}
