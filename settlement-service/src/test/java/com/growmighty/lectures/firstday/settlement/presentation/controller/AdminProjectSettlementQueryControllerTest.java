// TODO(settlement-plan): Verify admin responses expose review and payout state without leaking PG or event internals.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmProjectSettlementCommand;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
    private ProjectRefundRequestedRepository refundRequestedRepository;

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
    @DisplayName("관리자는 지급 행을 확정 시각과 식별자의 역순으로 조회한다")
    void returnsPayoutEntriesInDeterministicOrder() throws Exception {
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
                .andExpect(jsonPath("$.data[0].type").value("PAYOUT"))
                .andExpect(jsonPath("$.data[0].projectId").value(82_000_003L))
                .andExpect(jsonPath("$.data[0].payout.settlementId").value(sameTimeHigherId.settlementId()))
                .andExpect(jsonPath("$.data[0].payout.creatorId").value(firstCreatorId))
                .andExpect(jsonPath("$.data[0].payout.settlementBaseAmount").value(1_000_000))
                .andExpect(jsonPath("$.data[0].payout.creatorPayoutAmount").value(912_000))
                .andExpect(jsonPath("$.data[0].payout.status").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data[0].payout.confirmedAt").value("2026-07-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].payout.scheduledDate").value("2026-07-07"))
                .andExpect(jsonPath("$.data[0].payout.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].refund").isEmpty())
                .andExpect(jsonPath("$.data[1].payout.settlementId").value(sameTimeLowerId.settlementId()))
                .andExpect(jsonPath("$.data[1].payout.creatorId").value(secondCreatorId))
                .andExpect(jsonPath("$.data[2].payout.settlementId").value(oldest.settlementId()))
                .andExpect(content().string(not(containsString("admin-list-ref-payout"))))
                .andExpect(content().string(not(containsString("admin-list-idempotency"))))
                .andExpect(content().string(not(containsString("admin-list-toss-payout"))))
                .andExpect(content().string(not(containsString("ADMIN_LIST_INVALID_ACCOUNT"))));
    }

    @Test
    @DisplayName("관리자는 환불 요청의 발행 상태와 batch 처리 상태를 구분해 조회한다")
    void returnsRefundEntriesWithProcessingStatus() throws Exception {
        ProjectRefundRequested awaiting = refundRequest(
                85_000_001L, 95_000_001L, Instant.parse("2026-08-01T00:00:00Z")
        );
        ProjectRefundRequested actionRequired = refundRequest(
                85_000_002L, 95_000_002L, Instant.parse("2026-08-02T00:00:00Z")
        );
        actionRequired.markPublished(Instant.parse("2026-08-02T00:01:00Z"));
        actionRequired.recordPaymentResult("FAILED", Instant.parse("2026-08-02T00:02:00Z"), List.of(95_000_002L));
        refundRequestedRepository.save(awaiting);
        refundRequestedRepository.save(actionRequired);

        mockMvc.perform(get("/api/v1/settlements/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("REFUND"))
                .andExpect(jsonPath("$.data[0].projectId").value(85_000_002L))
                .andExpect(jsonPath("$.data[0].payout").isEmpty())
                .andExpect(jsonPath("$.data[0].refund.reason").value("PROJECT_FAILED"))
                .andExpect(jsonPath("$.data[0].refund.publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data[0].refund.processingStatus").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data[0].refund.paymentResultAt").value("2026-08-02T09:02:00+09:00"))
                .andExpect(jsonPath("$.data[0].refund.paymentCount").value(1))
                .andExpect(jsonPath("$.data[1].refund.publishStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data[1].refund.processingStatus").value("AWAITING_RESULT"))
                .andExpect(jsonPath("$.data[1].refund.paymentResultAt").isEmpty());
    }

    @Test
    @DisplayName("관리자는 프로젝트 식별자로 환불 batch 상세를 조회한다")
    void returnsRefundDetail() throws Exception {
        long projectId = 86_000_001L;
        ProjectRefundRequested request = refundRequest(
                projectId, 96_000_001L, Instant.parse("2026-08-03T00:00:00Z")
        );
        request.markPublished(Instant.parse("2026-08-03T00:01:00Z"));
        request.recordPaymentResult("COMPLETED", Instant.parse("2026-08-03T00:02:00Z"), List.of(96_000_001L));
        refundRequestedRepository.save(request);

        mockMvc.perform(get("/api/v1/settlements/all/refunds/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.reason").value("PROJECT_FAILED"))
                .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.processingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.paymentResultAt").value("2026-08-03T09:02:00+09:00"))
                .andExpect(jsonPath("$.data.payments.length()").value(1))
                .andExpect(jsonPath("$.data.payments[0].orderId").value(96_000_001L))
                .andExpect(jsonPath("$.data.payments[0].pgOrderId").value("PG-96000001"));
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
                .andExpect(jsonPath("$.data.payout.settlementId").value(confirmed.settlementId()))
                .andExpect(jsonPath("$.data.payout.payoutObligationId").doesNotExist())
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
    @DisplayName("존재하지 않는 환불 batch 상세는 찾을 수 없음으로 응답한다")
    void returnsNotFoundForMissingRefundRequest() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/all/refunds/{projectId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("프로젝트 환불 요청 내역을 찾을 수 없습니다."));
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
        PayoutObligation payoutObligation = payoutObligationRepository.findBySettlementId(confirmed.settlementId())
                .orElseThrow();
        PayoutAttempt attempt = payoutObligation.startAttempt(
                "admin-list-ref-payout",
                "admin-list-idempotency",
                LocalDateTime.of(2026, 7, 7, 9, 0)
        );
        payoutObligation.failAttempt(
                attempt,
                "admin-list-toss-payout",
                "ADMIN_LIST_INVALID_ACCOUNT",
                LocalDateTime.of(2026, 7, 7, 9, 0, 3),
                false
        );
        payoutObligationRepository.save(payoutObligation);
    }

    private PayoutObligation failThenCompletePayout(ConfirmedProjectSettlement confirmed) {
        PayoutObligation payoutObligation = payoutObligationRepository.findBySettlementId(confirmed.settlementId())
                .orElseThrow();
        PayoutAttempt firstAttempt = payoutObligation.startAttempt(
                "admin-detail-ref-1",
                "admin-detail-key-1",
                LocalDateTime.of(2026, 7, 7, 9, 0)
        );
        payoutObligation.failAttempt(
                firstAttempt,
                "admin-detail-toss-1",
                "BANK_TEMPORARILY_UNAVAILABLE",
                LocalDateTime.of(2026, 7, 7, 9, 0, 3),
                true
        );
        payoutObligation = payoutObligationRepository.save(payoutObligation);

        PayoutAttempt secondAttempt = payoutObligation.startAttempt(
                "admin-detail-ref-2",
                "admin-detail-key-2",
                LocalDateTime.of(2026, 7, 8, 9, 0)
        );
        payoutObligation.completeAttempt(
                secondAttempt,
                "admin-detail-toss-2",
                LocalDateTime.of(2026, 7, 8, 9, 0, 3)
        );
        return payoutObligationRepository.save(payoutObligation);
    }

    private static ProjectRefundRequested refundRequest(long projectId, long orderId, Instant occurredAt) {
        return ProjectRefundRequested.request(
                UUID.randomUUID().toString(),
                ProjectOutcomeFact.of(projectId, 99L, ProjectOutcomeFact.Outcome.FAILED, occurredAt),
                List.of(OrderPaymentFact.completed(
                        orderId,
                        "PG-" + orderId,
                        projectId,
                        Money.wons(50_000),
                        occurredAt.minusSeconds(1)
                )),
                occurredAt
        );
    }
}
