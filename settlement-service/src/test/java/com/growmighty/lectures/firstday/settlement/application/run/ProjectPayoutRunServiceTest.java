package com.growmighty.lectures.firstday.settlement.application.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPayoutRun;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectPayoutRunRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectPayoutRunServiceTest extends MySqlIntegrationTestSupport {

    @Autowired private ProjectPayoutRunService service;
    @Autowired private CreatorPayoutProfileRepository creatorPayoutProfileRepository;
    @Autowired private ProjectSettlementRepository projectSettlementRepository;
    @Autowired private PayoutObligationRepository payoutObligationRepository;
    @Autowired private SpringDataProjectOutcomeFactRepository outcomeRepository;
    @Autowired private SpringDataOrderPaymentFactRepository paymentRepository;
    @Autowired private SpringDataProjectPayoutRunRepository runRepository;

    @Test
    @DisplayName("전체 완료 결제가 모두 대사 완료된 성공 프로젝트만 지급하고 재실행해도 중복 지급하지 않는다")
    void paysOnlyFullyReconciledProjectsWithoutDuplicatingSuccessfulPayouts() {
        creatorPayoutProfileRepository.save(profile(10L));
        creatorPayoutProfileRepository.save(profile(20L));
        outcomeRepository.save(outcome(1L, 10L));
        outcomeRepository.save(outcome(2L, 20L));
        outcomeRepository.save(outcome(3L, 30L));
        paymentRepository.save(reconciledPayment(101L, 1L, "2026-06-30T10:00:00Z"));
        paymentRepository.save(reconciledPayment(102L, 1L, "2026-07-31T10:00:00Z"));
        paymentRepository.save(reconciledPayment(201L, 2L, "2026-06-30T10:00:00Z"));
        paymentRepository.save(completedPayment(202L, 2L, "2026-07-31T10:00:00Z"));
        paymentRepository.save(reconciledPayment(301L, 3L, "2026-07-31T10:00:00Z"));
        YearMonth payoutMonth = YearMonth.of(2026, 8);

        service.run(payoutMonth);
        service.run(payoutMonth);

        Long settlementId = projectSettlementRepository.findByProjectId(1L).orElseThrow().id();
        Long deferredSettlementId = projectSettlementRepository.findByProjectId(3L).orElseThrow().id();
        assertThat(projectSettlementRepository.findByProjectId(2L)).isEmpty();
        assertThat(payoutObligationRepository.findBySettlementId(settlementId).orElseThrow())
                .extracting(obligation -> obligation.status(), obligation -> obligation.attemptCount())
                .containsExactly(PayoutStatus.COMPLETED, 1);
        assertThat(creatorPayoutProfileRepository.findByCreatorId(30L).orElseThrow().status())
                .isEqualTo(CreatorPayoutStatus.REGISTRATION_PENDING);
        assertThat(payoutObligationRepository.findBySettlementId(deferredSettlementId)).isEmpty();
        assertThat(runRepository.findAll()).extracting(ProjectPayoutRun::status)
                .containsOnly(ProjectPayoutRun.Status.COMPLETED);
        assertThat(runRepository.findAll()).hasSize(2);
    }

    private static CreatorPayoutProfile profile(Long creatorId) {
        return CreatorPayoutProfile.registered(
                creatorId,
                "seller-" + creatorId,
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 8, 1, 9, 0)
        );
    }

    private static ProjectOutcomeFact outcome(Long projectId, Long creatorId) {
        return ProjectOutcomeFact.of(
                projectId,
                "프로젝트 " + projectId,
                creatorId,
                ProjectOutcomeFact.Outcome.SUCCEEDED,
                Instant.parse("2026-07-31T10:00:00Z")
        );
    }

    private static OrderPaymentFact reconciledPayment(Long orderId, Long projectId, String completedAt) {
        OrderPaymentFact payment = completedPayment(orderId, projectId, completedAt);
        payment.confirmReconciliation();
        return payment;
    }

    private static OrderPaymentFact completedPayment(Long orderId, Long projectId, String completedAt) {
        return OrderPaymentFact.completed(
                orderId,
                "pg-" + orderId,
                projectId,
                Money.wons(100_000),
                Instant.parse(completedAt)
        );
    }
}
