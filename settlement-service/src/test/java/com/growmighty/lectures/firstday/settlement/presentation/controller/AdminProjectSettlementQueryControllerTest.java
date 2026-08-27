// TODO(settlement-plan): Verify admin responses expose review and payout state without leaking PG or event internals.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmProjectSettlementCommand;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformation;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationReader;
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
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @Autowired
    private SpringDataProjectOutcomeFactRepository outcomeRepository;

    @MockitoBean
    private CreatorInformationReader creatorInformationReader;

    @Test
    @DisplayName("관리자는 등록 대기 창작자의 셀러 등록을 결정적 더미 결과로 완료한다")
    void registersPendingCreatorPayoutProfile() throws Exception {
        long creatorId = 80_000_001L;
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.awaitingRegistration(creatorId));
        when(creatorInformationReader.read(creatorId)).thenReturn(new CreatorInformation(
                "creator@example.com", "창작자", "01012345678"
        ));

        mockMvc.perform(post("/api/v1/settlements/creator-payout-profiles/{creatorId}/registration", creatorId)
                        .header(JwtHeaders.USER_ROLE, UserRole.ADMIN.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(creatorPayoutProfileRepository.findByCreatorId(creatorId).orElseThrow())
                .extracting(CreatorPayoutProfile::status, CreatorPayoutProfile::tossSellerId)
                .containsExactly(CreatorPayoutStatus.PAYOUT_READY, "dummy-seller-" + creatorId);
    }

    @Test
    @DisplayName("관리자가 아니면 셀러 등록 대행을 실행할 수 없다")
    void rejectsNonAdminSellerRegistration() throws Exception {
        long creatorId = 80_000_002L;
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.awaitingRegistration(creatorId));

        mockMvc.perform(post("/api/v1/settlements/creator-payout-profiles/{creatorId}/registration", creatorId)
                        .header(JwtHeaders.USER_ROLE, UserRole.CREATOR.name()))
                .andExpect(status().isForbidden());

        assertThat(creatorPayoutProfileRepository.findByCreatorId(creatorId).orElseThrow().status())
                .isEqualTo(CreatorPayoutStatus.REGISTRATION_PENDING);
    }

    @Test
    @DisplayName("등록 완료된 창작자의 셀러 등록 대행은 거부한다")
    void rejectsAlreadyRegisteredCreatorPayoutProfile() throws Exception {
        long creatorId = 80_000_003L;
        savePayoutReadyProfile(creatorId);

        mockMvc.perform(post("/api/v1/settlements/creator-payout-profiles/{creatorId}/registration", creatorId)
                        .header(JwtHeaders.USER_ROLE, UserRole.ADMIN.name()))
                .andExpect(status().isConflict());
    }

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
    @DisplayName("관리자는 성공 프로젝트의 등록 대기 정산을 지급과 구분해 조회한다")
    void returnsRegistrationPendingEntryForSucceededProject() throws Exception {
        long creatorId = 80_100_001L;
        ConfirmedProjectSettlement confirmed = confirm(
                80_200_001L,
                creatorId,
                LocalDateTime.of(2026, 7, 1, 10, 0)
        );
        outcomeRepository.save(ProjectOutcomeFact.of(
                80_200_002L,
                "실패 프로젝트",
                80_100_002L,
                ProjectOutcomeFact.Outcome.FAILED,
                Instant.parse("2026-07-01T01:00:00Z")
        ));
        projectSettlementService.confirm(new ConfirmProjectSettlementCommand(
                80_200_002L,
                80_100_002L,
                List.of(Money.wons(1_000_000)),
                LocalDate.of(2026, 7, 7),
                LocalDateTime.of(2026, 7, 1, 10, 0)
        ));

        mockMvc.perform(get("/api/v1/settlements/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("REGISTRATION_PENDING"))
                .andExpect(jsonPath("$.data[0].projectId").value(80_200_001L))
                .andExpect(jsonPath("$.data[0].projectName").value("프로젝트 80200001"))
                .andExpect(jsonPath("$.data[0].refundRequestId").isEmpty())
                .andExpect(jsonPath("$.data[0].payout").isEmpty())
                .andExpect(jsonPath("$.data[0].refund").isEmpty())
                .andExpect(jsonPath("$.data[0].registrationPending.settlementId").value(confirmed.settlementId()))
                .andExpect(jsonPath("$.data[0].registrationPending.creatorId").value(creatorId))
                .andExpect(jsonPath("$.data[0].registrationPending.settlementBaseAmount").value(1_000_000))
                .andExpect(jsonPath("$.data[0].registrationPending.creatorPayoutAmount").value(912_000))
                .andExpect(jsonPath("$.data[0].registrationPending.confirmedAt")
                        .value("2026-07-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].registrationPending.status").doesNotExist())
                .andExpect(jsonPath("$.data[0].registrationPending.payoutObligationId").doesNotExist());
    }

    @Test
    @DisplayName("관리자는 기본 발행 시각 정렬로 지급 행을 조회한다")
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
                .andExpect(jsonPath("$.data[0].projectId").value(82_000_002L))
                .andExpect(jsonPath("$.data[0].projectName").value("프로젝트 82000002"))
                .andExpect(jsonPath("$.data[0].refundRequestId").isEmpty())
                .andExpect(jsonPath("$.data[0].payout.settlementId").value(sameTimeLowerId.settlementId()))
                .andExpect(jsonPath("$.data[0].payout.creatorId").value(secondCreatorId))
                .andExpect(jsonPath("$.data[0].payout.settlementBaseAmount").value(1_000_000))
                .andExpect(jsonPath("$.data[0].payout.creatorPayoutAmount").value(912_000))
                .andExpect(jsonPath("$.data[0].payout.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].payout.confirmedAt").value("2026-07-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].payout.scheduledDate").value("2026-07-07"))
                .andExpect(jsonPath("$.data[0].payout.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].refund").isEmpty())
                .andExpect(jsonPath("$.data[1].payout.settlementId").value(sameTimeHigherId.settlementId()))
                .andExpect(jsonPath("$.data[1].payout.creatorId").value(firstCreatorId))
                .andExpect(jsonPath("$.data[2].payout.settlementId").value(oldest.settlementId()))
                .andExpect(content().string(not(containsString("admin-list-ref-payout"))))
                .andExpect(content().string(not(containsString("admin-list-idempotency"))))
                .andExpect(content().string(not(containsString("admin-list-toss-payout"))))
                .andExpect(content().string(not(containsString("ADMIN_LIST_INVALID_ACCOUNT"))));
    }

    @Test
    @DisplayName("관리자는 환불 행을 refundRequestId와 통합 환불 상태로 조회한다")
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
                .andExpect(jsonPath("$.data[0].projectName").value("프로젝트 85000002"))
                .andExpect(jsonPath("$.data[0].refundRequestId").value(actionRequired.refundRequestId()))
                .andExpect(jsonPath("$.data[0].payout").isEmpty())
                .andExpect(jsonPath("$.data[0].refund.reason").value("PROJECT_FAILED"))
                .andExpect(jsonPath("$.data[0].refund.refundStatus").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data[0].refund.paymentResultAt").value("2026-08-02T09:02:00+09:00"))
                .andExpect(jsonPath("$.data[0].refund.paymentCount").value(1))
                .andExpect(jsonPath("$.data[1].refund.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data[1].refund.paymentResultAt").isEmpty());
    }

    @Test
    @DisplayName("관리자는 프로젝트명 오름차순으로 지급과 환불 행을 정렬한다")
    void sortsEntriesByProjectName() throws Exception {
        long creatorId = 82_100_001L;
        savePayoutReadyProfile(creatorId);
        confirm(82_100_001L, "나무", creatorId, LocalDateTime.of(2026, 7, 1, 10, 0));
        ProjectRefundRequested refund = refundRequest(82_100_002L, "가방", 95_100_001L, Instant.parse("2026-08-01T00:00:00Z"));
        refundRequestedRepository.save(refund);

        mockMvc.perform(get("/api/v1/settlements/all").param("sort", "NAME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].projectName").value("가방"))
                .andExpect(jsonPath("$.data[0].type").value("REFUND"))
                .andExpect(jsonPath("$.data[1].projectName").value("나무"))
                .andExpect(jsonPath("$.data[1].type").value("PAYOUT"));
    }

    @Test
    @DisplayName("관리자는 처리 시각 최신순으로 완료된 지급과 환불 행을 정렬한다")
    void sortsEntriesByProcessedAt() throws Exception {
        long creatorId = 82_200_001L;
        savePayoutReadyProfile(creatorId);
        ConfirmedProjectSettlement payout = confirm(82_200_001L, creatorId, LocalDateTime.of(2026, 7, 1, 10, 0));
        failThenCompletePayout(payout);
        ProjectRefundRequested refund = refundRequest(82_200_002L, 95_200_001L, Instant.parse("2026-08-01T00:00:00Z"));
        refund.markPublished(Instant.parse("2026-08-01T00:01:00Z"));
        refund.recordPaymentResult("COMPLETED", Instant.parse("2026-08-01T00:02:00Z"), List.of(95_200_001L));
        refundRequestedRepository.save(refund);

        mockMvc.perform(get("/api/v1/settlements/all").param("sort", "PROCESSED_AT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("REFUND"))
                .andExpect(jsonPath("$.data[0].refund.refundStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data[1].type").value("PAYOUT"));
    }

    @Test
    @DisplayName("관리자는 refundRequestId로 조치 필요 환불 batch 상세를 조회한다")
    void returnsRefundDetail() throws Exception {
        long projectId = 86_000_001L;
        ProjectRefundRequested request = refundRequest(
                projectId, 96_000_001L, Instant.parse("2026-08-03T00:00:00Z")
        );
        request.markPublished(Instant.parse("2026-08-03T00:01:00Z"));
        request.recordPaymentResult("FAILED", Instant.parse("2026-08-03T00:02:00Z"), List.of(96_000_001L));
        refundRequestedRepository.save(request);

        mockMvc.perform(get("/api/v1/settlements/all/refunds/{refundRequestId}", request.refundRequestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundRequestId").value(request.refundRequestId()))
                .andExpect(jsonPath("$.data.projectId").value(projectId))
                .andExpect(jsonPath("$.data.projectName").value("프로젝트 " + projectId))
                .andExpect(jsonPath("$.data.reason").value("PROJECT_FAILED"))
                .andExpect(jsonPath("$.data.refundStatus").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data.publishStatus").doesNotExist())
                .andExpect(jsonPath("$.data.processingStatus").doesNotExist())
                .andExpect(jsonPath("$.data.paymentResultAt").value("2026-08-03T09:02:00+09:00"))
                .andExpect(jsonPath("$.data.payments.length()").value(1))
                .andExpect(jsonPath("$.data.payments[0].orderId").value(96_000_001L))
                .andExpect(jsonPath("$.data.payments[0].pgOrderId").value("PG-96000001"))
                .andExpect(jsonPath("$.data.payments[0].actionRequired").value(true));
    }

    @Test
    @DisplayName("관리자는 발행 전 환불 batch를 요청 상태로 조회한다")
    void returnsRequestedRefundDetail() throws Exception {
        ProjectRefundRequested request = refundRequest(86_000_002L, 96_000_002L, Instant.parse("2026-08-03T00:00:00Z"));
        refundRequestedRepository.save(request);

        mockMvc.perform(get("/api/v1/settlements/all/refunds/{refundRequestId}", request.refundRequestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.paymentResultAt").isEmpty());
    }

    @Test
    @DisplayName("관리자는 결과 대기 및 완료 환불 batch의 통합 상태를 조회한다")
    void returnsProcessingAndCompletedRefundDetails() throws Exception {
        ProjectRefundRequested processing = refundRequest(86_000_003L, 96_000_003L, Instant.parse("2026-08-03T00:00:00Z"));
        processing.markPublished(Instant.parse("2026-08-03T00:01:00Z"));
        ProjectRefundRequested completed = refundRequest(86_000_004L, 96_000_004L, Instant.parse("2026-08-03T00:00:00Z"));
        completed.markPublished(Instant.parse("2026-08-03T00:01:00Z"));
        completed.recordPaymentResult("COMPLETED", Instant.parse("2026-08-03T00:02:00Z"), List.of(96_000_004L));
        refundRequestedRepository.save(processing);
        refundRequestedRepository.save(completed);

        mockMvc.perform(get("/api/v1/settlements/all/refunds/{refundRequestId}", processing.refundRequestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("PROCESSING"));
        mockMvc.perform(get("/api/v1/settlements/all/refunds/{refundRequestId}", completed.refundRequestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("존재하지 않는 refundRequestId의 환불 batch 상세는 찾을 수 없다")
    void doesNotReturnRefundDetailForUnknownRefundRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/all/refunds/{refundRequestId}", "unknown-refund-request"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리자 상세는 지급 시도와 원본 오류를 순서대로 제공하고 계좌 정보를 노출하지 않는다")
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
                .andExpect(jsonPath("$.data.payout.destination.bankCode").doesNotExist())
                .andExpect(jsonPath("$.data.payout.destination.maskedAccountNumber").doesNotExist())
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
        return confirm(projectId, "프로젝트 " + projectId, creatorId, confirmedAt);
    }

    private ConfirmedProjectSettlement confirm(
            long projectId,
            String projectName,
            long creatorId,
            LocalDateTime confirmedAt
    ) {
        outcomeRepository.save(ProjectOutcomeFact.of(
                projectId,
                projectName,
                creatorId,
                ProjectOutcomeFact.Outcome.SUCCEEDED,
                confirmedAt.atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant()
        ));
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
                CreatorPayoutStatus.PAYOUT_READY
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

    private ProjectRefundRequested refundRequest(long projectId, long orderId, Instant occurredAt) {
        return refundRequest(projectId, "프로젝트 " + projectId, orderId, occurredAt);
    }

    private ProjectRefundRequested refundRequest(long projectId, String projectName, long orderId, Instant occurredAt) {
        ProjectOutcomeFact outcome = ProjectOutcomeFact.of(
                projectId, projectName, 99L, ProjectOutcomeFact.Outcome.FAILED, occurredAt
        );
        outcomeRepository.save(outcome);
        return ProjectRefundRequested.request(
                null,
                outcome,
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
