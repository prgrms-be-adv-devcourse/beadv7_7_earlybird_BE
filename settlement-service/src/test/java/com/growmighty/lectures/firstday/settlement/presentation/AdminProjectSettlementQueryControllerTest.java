package com.growmighty.lectures.firstday.settlement.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.settlement.application.ConfirmProjectSettlementCommand;
import com.growmighty.lectures.firstday.settlement.application.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.SettlementCalculationPolicy;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminProjectSettlementQueryControllerTest extends MySqlIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private PayoutObligationRepository payoutObligationRepository;

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

    @Test
    @DisplayName("프로젝트 정산 내역이 없으면 관리자는 빈 목록을 조회한다")
    void returnsEmptyListWhenNoProjectSettlementsExist() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("관리자는 모든 프로젝트 정산을 확정 시각과 식별자의 역순으로 조회한다")
    void returnsAllProjectSettlementsInDeterministicOrder() throws Exception {
        long firstCreatorId = 81_000_001L;
        long secondCreatorId = 81_000_002L;
        savePayoutReadyProfile(firstCreatorId);
        savePayoutReadyProfile(secondCreatorId);

        ConfirmedProjectSettlement oldest = confirm(
                82_000_001L,
                firstCreatorId,
                LocalDateTime.of(2026, 6, 1, 9, 0)
        );
        ConfirmedProjectSettlement sameTimeLowerId = confirm(
                82_000_002L,
                secondCreatorId,
                LocalDateTime.of(2026, 7, 1, 10, 0)
        );
        ConfirmedProjectSettlement sameTimeHigherId = confirm(
                82_000_003L,
                firstCreatorId,
                LocalDateTime.of(2026, 7, 1, 10, 0)
        );
        failPayoutAttempt(sameTimeHigherId);

        mockMvc.perform(get("/api/v1/settlements/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].settlementId").value(sameTimeHigherId.settlementId()))
                .andExpect(jsonPath("$.data[0].projectId").value(82_000_003L))
                .andExpect(jsonPath("$.data[0].projectTitle").doesNotExist())
                .andExpect(jsonPath("$.data[0].creatorId").value(firstCreatorId))
                .andExpect(jsonPath("$.data[0].settlementBaseAmount").value(1_000_000))
                .andExpect(jsonPath("$.data[0].creatorPayoutAmount").value(912_000))
                .andExpect(jsonPath("$.data[0].status").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data[0].confirmedAt").value("2026-07-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].scheduledDate").value("2026-07-07"))
                .andExpect(jsonPath("$.data[0].completedAt").isEmpty())
                .andExpect(jsonPath("$.data[0].payoutObligationId").doesNotExist())
                .andExpect(jsonPath("$.data[0].tossSellerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].attempts").doesNotExist())
                .andExpect(jsonPath("$.data[0].errorCode").doesNotExist())
                .andExpect(jsonPath("$.data[1].settlementId").value(sameTimeLowerId.settlementId()))
                .andExpect(jsonPath("$.data[1].creatorId").value(secondCreatorId))
                .andExpect(jsonPath("$.data[2].settlementId").value(oldest.settlementId()))
                .andExpect(content().string(not(containsString("admin-list-ref-payout"))))
                .andExpect(content().string(not(containsString("admin-list-idempotency"))))
                .andExpect(content().string(not(containsString("admin-list-toss-payout"))))
                .andExpect(content().string(not(containsString("ADMIN_LIST_INVALID_ACCOUNT"))));
    }

    @Test
    @DisplayName("관리자 상세는 지급 시도와 원본 오류를 순서대로 제공하고 보관하지 않는 정보를 노출하지 않는다")
    void returnsAdminDetailWithOrderedPayoutAttemptsWithinExposureBoundary() throws Exception {
        long creatorId = 83_000_001L;
        savePayoutReadyProfile(creatorId);
        ConfirmedProjectSettlement confirmed = confirm(
                84_000_001L,
                creatorId,
                LocalDateTime.of(2026, 7, 1, 9, 0)
        );
        PayoutObligation completedObligation = failThenCompletePayout(confirmed);
        PayoutAttempt failedAttempt = completedObligation.attempts().getFirst();
        PayoutAttempt completedAttempt = completedObligation.attempts().getLast();

        mockMvc.perform(get("/api/v1/settlements/all/{settlementId}", confirmed.settlementId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlementId").value(confirmed.settlementId()))
                .andExpect(jsonPath("$.data.creatorId").value(creatorId))
                .andExpect(jsonPath("$.data.project.projectId").value(84_000_001L))
                .andExpect(jsonPath("$.data.project.title").doesNotExist())
                .andExpect(jsonPath("$.data.confirmedAt").value("2026-07-01T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.breakdown.settlementBaseAmount").value(1_000_000))
                .andExpect(jsonPath("$.data.breakdown.paymentAndSettlementAgencyFee.rate").value(0.04))
                .andExpect(jsonPath("$.data.breakdown.paymentAndSettlementAgencyFee.amount").value(40_000))
                .andExpect(jsonPath("$.data.breakdown.paymentAndSettlementAgencyFee.vatRate").value(0.10))
                .andExpect(jsonPath("$.data.breakdown.paymentAndSettlementAgencyFee.vatAmount").value(4_000))
                .andExpect(jsonPath("$.data.breakdown.platformFee.rate").value(0.04))
                .andExpect(jsonPath("$.data.breakdown.platformFee.amount").value(40_000))
                .andExpect(jsonPath("$.data.breakdown.platformFee.vatRate").value(0.10))
                .andExpect(jsonPath("$.data.breakdown.platformFee.vatAmount").value(4_000))
                .andExpect(jsonPath("$.data.breakdown.otherDeductionAmount").value(0))
                .andExpect(jsonPath("$.data.breakdown.creatorPayoutAmount").value(912_000))
                .andExpect(jsonPath("$.data.payout.payoutObligationId").value(confirmed.payoutObligationId()))
                .andExpect(jsonPath("$.data.payout.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payout.scheduledDate").value("2026-07-07"))
                .andExpect(jsonPath("$.data.payout.completedAt").value("2026-07-08T09:00:03+09:00"))
                .andExpect(jsonPath("$.data.payout.destination.tossSellerId").value("seller-83000001"))
                .andExpect(jsonPath("$.data.payout.destination.bankCode").value("088"))
                .andExpect(jsonPath("$.data.payout.destination.maskedAccountNumber").value("********0001"))
                .andExpect(jsonPath("$.data.payout.attempts.length()").value(2))
                .andExpect(jsonPath("$.data.payout.attempts[0].attemptId").value(failedAttempt.id()))
                .andExpect(jsonPath("$.data.payout.attempts[0].sequence").value(1))
                .andExpect(jsonPath("$.data.payout.attempts[0].refPayoutId").value("admin-detail-ref-1"))
                .andExpect(jsonPath("$.data.payout.attempts[0].idempotencyKey").value("admin-detail-key-1"))
                .andExpect(jsonPath("$.data.payout.attempts[0].tossPayoutId").value("admin-detail-toss-1"))
                .andExpect(jsonPath("$.data.payout.attempts[0].amount").value(912_000))
                .andExpect(jsonPath("$.data.payout.attempts[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.payout.attempts[0].errorCode")
                        .value("BANK_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.payout.attempts[0].requestedAt")
                        .value("2026-07-07T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.payout.attempts[0].completedAt")
                        .value("2026-07-07T09:00:03+09:00"))
                .andExpect(jsonPath("$.data.payout.attempts[1].attemptId").value(completedAttempt.id()))
                .andExpect(jsonPath("$.data.payout.attempts[1].sequence").value(2))
                .andExpect(jsonPath("$.data.payout.attempts[1].refPayoutId").value("admin-detail-ref-2"))
                .andExpect(jsonPath("$.data.payout.attempts[1].idempotencyKey").value("admin-detail-key-2"))
                .andExpect(jsonPath("$.data.payout.attempts[1].tossPayoutId").value("admin-detail-toss-2"))
                .andExpect(jsonPath("$.data.payout.attempts[1].amount").value(912_000))
                .andExpect(jsonPath("$.data.payout.attempts[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payout.attempts[1].errorCode").isEmpty())
                .andExpect(jsonPath("$.data.payout.attempts[1].requestedAt")
                        .value("2026-07-08T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.payout.attempts[1].completedAt")
                        .value("2026-07-08T09:00:03+09:00"))
                .andExpect(jsonPath("$.data.payout.statusHistory").doesNotExist())
                .andExpect(jsonPath("$.data.payout.destination.accountNumber").doesNotExist())
                .andExpect(jsonPath("$.data.payout.attempts[0].requestBody").doesNotExist())
                .andExpect(jsonPath("$.data.backers").doesNotExist())
                .andExpect(jsonPath("$.data.payments").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 프로젝트 정산 상세는 공개 오류 코드 없이 찾을 수 없음으로 응답한다")
    void returnsNotFoundForMissingProjectSettlement() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/all/{settlementId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 내역을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("관리자 상세도 지급 의무 누락을 숨기지 않고 데이터 불일치로 응답한다")
    void rejectsAdminDetailWhenPayoutObligationIsMissing() throws Exception {
        long creatorId = 85_000_001L;
        CreatorPayoutProfile payoutProfile = payoutReadyProfile(creatorId);
        creatorPayoutProfileRepository.save(payoutProfile);
        ProjectSettlement settlement = projectSettlementRepository.save(ProjectSettlement.confirm(
                86_000_001L,
                creatorId,
                SettlementCalculationPolicy.current().feePolicySnapshot(),
                SettlementCalculationPolicy.current().calculate(List.of(Money.wons(1_000_000))),
                payoutProfile.snapshotDestination(),
                LocalDateTime.of(2026, 7, 1, 9, 0)
        ));

        mockMvc.perform(get("/api/v1/settlements/all/{settlementId}", settlement.id()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 데이터가 일치하지 않습니다."))
                .andExpect(content().string(not(containsString("지급 의무가 존재하지 않습니다"))));
    }

    private ConfirmedProjectSettlement confirm(
            long projectId,
            long creatorId,
            LocalDateTime confirmedAt
    ) {
        return projectSettlementService.confirm(new ConfirmProjectSettlementCommand(
                projectId,
                creatorId,
                List.of(Money.wons(1_000_000)),
                LocalDate.of(2026, 7, 7),
                confirmedAt
        ));
    }

    private void savePayoutReadyProfile(long creatorId) {
        creatorPayoutProfileRepository.save(payoutReadyProfile(creatorId));
    }

    private CreatorPayoutProfile payoutReadyProfile(long creatorId) {
        return CreatorPayoutProfile.registered(
                creatorId,
                "seller-" + creatorId,
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********%04d".formatted(creatorId % 10_000),
                LocalDateTime.of(2026, 6, 1, 8, 0)
        );
    }

    private void failPayoutAttempt(ConfirmedProjectSettlement confirmed) {
        PayoutObligation obligation = payoutObligationRepository.findById(confirmed.payoutObligationId())
                .orElseThrow();
        PayoutAttempt attempt = obligation.startAttempt(
                "admin-list-ref-payout",
                "admin-list-idempotency",
                LocalDateTime.of(2026, 7, 7, 9, 0)
        );
        obligation.failAttempt(
                attempt,
                "admin-list-toss-payout",
                "ADMIN_LIST_INVALID_ACCOUNT",
                LocalDateTime.of(2026, 7, 7, 9, 0, 3),
                false
        );
        payoutObligationRepository.save(obligation);
    }

    private PayoutObligation failThenCompletePayout(ConfirmedProjectSettlement confirmed) {
        PayoutObligation obligation = payoutObligationRepository.findById(confirmed.payoutObligationId())
                .orElseThrow();
        PayoutAttempt firstAttempt = obligation.startAttempt(
                "admin-detail-ref-1",
                "admin-detail-key-1",
                LocalDateTime.of(2026, 7, 7, 9, 0)
        );
        obligation.failAttempt(
                firstAttempt,
                "admin-detail-toss-1",
                "BANK_TEMPORARILY_UNAVAILABLE",
                LocalDateTime.of(2026, 7, 7, 9, 0, 3),
                true
        );
        obligation = payoutObligationRepository.save(obligation);

        PayoutAttempt secondAttempt = obligation.startAttempt(
                "admin-detail-ref-2",
                "admin-detail-key-2",
                LocalDateTime.of(2026, 7, 8, 9, 0)
        );
        obligation.completeAttempt(
                secondAttempt,
                "admin-detail-toss-2",
                LocalDateTime.of(2026, 7, 8, 9, 0, 3)
        );
        return payoutObligationRepository.save(obligation);
    }
}
