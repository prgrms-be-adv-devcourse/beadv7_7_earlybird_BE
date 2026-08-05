package com.growmighty.lectures.firstday.settlement.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmProjectSettlementCommand;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementCalculationPolicy;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreatorProjectSettlementQueryControllerTest extends MySqlIntegrationTestSupport {

    private static final String CREATOR_ID = "70000001";

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
    @DisplayName("Gateway 전달 사용자 식별자 없이 창작자 프로젝트 정산 조회를 요청하면 거부한다")
    void rejectsCreatorQueryWithoutForwardedUserId() throws Exception {
        mockMvc.perform(get("/api/v1/settlements"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("프로젝트 정산 내역이 없는 창작자는 빈 목록을 조회한다")
    void returnsEmptyListWhenCreatorHasNoProjectSettlements() throws Exception {
        mockMvc.perform(get("/api/v1/settlements")
                        .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("창작자는 본인 프로젝트 정산 내역만 확정 시각과 식별자의 역순으로 조회한다")
    void returnsOwnedProjectSettlementsInDeterministicOrder() throws Exception {
        long creatorId = Long.parseLong(CREATOR_ID);
        long otherCreatorId = 70_000_002L;
        savePayoutReadyProfile(creatorId);
        savePayoutReadyProfile(otherCreatorId);

        ConfirmedProjectSettlement oldest = confirm(
                71_000_001L,
                creatorId,
                LocalDateTime.of(2026, 6, 1, 9, 0)
        );
        ConfirmedProjectSettlement sameTimeLowerId = confirm(
                71_000_002L,
                creatorId,
                LocalDateTime.of(2026, 7, 1, 10, 0)
        );
        ConfirmedProjectSettlement sameTimeHigherId = confirm(
                71_000_003L,
                creatorId,
                LocalDateTime.of(2026, 7, 1, 10, 0)
        );
        confirm(
                71_000_004L,
                otherCreatorId,
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );

        mockMvc.perform(get("/api/v1/settlements")
                        .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].settlementId").value(sameTimeHigherId.settlementId()))
                .andExpect(jsonPath("$.data[0].projectId").value(71_000_003L))
                .andExpect(jsonPath("$.data[0].projectTitle").doesNotExist())
                .andExpect(jsonPath("$.data[0].settlementBaseAmount").value(1_000_000))
                .andExpect(jsonPath("$.data[0].creatorPayoutAmount").value(912_000))
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].confirmedAt").value("2026-07-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.data[0].scheduledDate").value("2026-07-07"))
                .andExpect(jsonPath("$.data[0].completedAt").isEmpty())
                .andExpect(jsonPath("$.data[0].creatorId").doesNotExist())
                .andExpect(jsonPath("$.data[0].payoutObligationId").doesNotExist())
                .andExpect(jsonPath("$.data[0].tossSellerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].attempts").doesNotExist())
                .andExpect(jsonPath("$.data[0].errorCode").doesNotExist())
                .andExpect(jsonPath("$.data[1].settlementId").value(sameTimeLowerId.settlementId()))
                .andExpect(jsonPath("$.data[2].settlementId").value(oldest.settlementId()));
    }

    @Test
    @DisplayName("창작자 상세는 확정 원본과 현재 지급 정보만 제공하고 운영 민감정보를 숨긴다")
    void returnsCreatorDetailWithoutPayoutOperationDetails() throws Exception {
        long creatorId = Long.parseLong(CREATOR_ID);
        savePayoutReadyProfile(creatorId);
        ConfirmedProjectSettlement confirmed = confirm(
                72_000_001L,
                creatorId,
                LocalDateTime.of(2026, 7, 1, 9, 0)
        );
        failPayoutAttempt(confirmed);

        mockMvc.perform(get("/api/v1/settlements/{settlementId}", confirmed.settlementId())
                        .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlementId").value(confirmed.settlementId()))
                .andExpect(jsonPath("$.data.project.projectId").value(72_000_001L))
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
                .andExpect(jsonPath("$.data.payout.status").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.data.payout.scheduledDate").value("2026-07-07"))
                .andExpect(jsonPath("$.data.payout.completedAt").isEmpty())
                .andExpect(jsonPath("$.data.payout.destination.bankCode").value("088"))
                .andExpect(jsonPath("$.data.payout.destination.maskedAccountNumber").value("********0001"))
                .andExpect(jsonPath("$.data.creatorId").doesNotExist())
                .andExpect(jsonPath("$.data.payout.payoutObligationId").doesNotExist())
                .andExpect(jsonPath("$.data.payout.destination.tossSellerId").doesNotExist())
                .andExpect(jsonPath("$.data.payout.attempts").doesNotExist())
                .andExpect(content().string(not(containsString("ref-payout-secret"))))
                .andExpect(content().string(not(containsString("idempotency-secret"))))
                .andExpect(content().string(not(containsString("toss-payout-secret"))))
                .andExpect(content().string(not(containsString("INVALID_ACCOUNT"))));
    }

    @Test
    @DisplayName("창작자 목록과 상세의 완료 상태·시각은 성공한 지급 결과에서 제공한다")
    void returnsCompletedStatusAndSuccessfulAttemptTime() throws Exception {
        long creatorId = Long.parseLong(CREATOR_ID);
        savePayoutReadyProfile(creatorId);
        ConfirmedProjectSettlement confirmed = confirm(
                73_000_001L,
                creatorId,
                LocalDateTime.of(2026, 7, 1, 9, 0)
        );
        completePayoutAttempt(confirmed);

        mockMvc.perform(get("/api/v1/settlements")
                        .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].completedAt").value("2026-07-07T09:00:03+09:00"));

        mockMvc.perform(get("/api/v1/settlements/{settlementId}", confirmed.settlementId())
                        .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payout.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payout.completedAt").value("2026-07-07T09:00:03+09:00"));
    }

    @Test
    @DisplayName("타인 소유와 존재하지 않는 프로젝트 정산 상세는 같은 응답으로 숨긴다")
    void hidesWhetherProjectSettlementExistsFromOtherCreator() throws Exception {
        long otherCreatorId = 70_000_002L;
        savePayoutReadyProfile(otherCreatorId);
        ConfirmedProjectSettlement otherCreatorSettlement = confirm(
                74_000_001L,
                otherCreatorId,
                LocalDateTime.of(2026, 7, 1, 9, 0)
        );

        MvcResult otherCreatorResult = mockMvc.perform(
                        get("/api/v1/settlements/{settlementId}", otherCreatorSettlement.settlementId())
                                .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 내역을 찾을 수 없습니다."))
                .andReturn();

        MvcResult missingResult = mockMvc.perform(get("/api/v1/settlements/{settlementId}", Long.MAX_VALUE)
                        .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(missingResult.getResponse().getContentAsString())
                .isEqualTo(otherCreatorResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("프로젝트 정산에 대응하는 지급 의무가 없으면 공개 정보 없이 데이터 불일치로 응답한다")
    void rejectsCreatorDetailWhenPayoutObligationIsMissing() throws Exception {
        long creatorId = Long.parseLong(CREATOR_ID);
        CreatorPayoutProfile payoutProfile = payoutReadyProfile(creatorId);
        creatorPayoutProfileRepository.save(payoutProfile);
        ProjectSettlement settlement = projectSettlementRepository.save(ProjectSettlement.confirm(
                75_000_001L,
                creatorId,
                SettlementCalculationPolicy.current().feePolicySnapshot(),
                SettlementCalculationPolicy.current().calculate(List.of(Money.wons(1_000_000))),
                payoutProfile.snapshotDestination(),
                LocalDateTime.of(2026, 7, 1, 9, 0)
        ));

        mockMvc.perform(get("/api/v1/settlements/{settlementId}", settlement.id())
                        .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 데이터가 일치하지 않습니다."))
                .andExpect(content().string(not(containsString("지급 의무가 존재하지 않습니다"))));
    }

    @Test
    @DisplayName("프로젝트 정산 식별자 형식이 올바르지 않으면 잘못된 요청으로 응답한다")
    void rejectsMalformedSettlementId() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/not-a-number")
                        .header(JwtHeaders.USER_ID, CREATOR_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
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
                "ref-payout-secret",
                "idempotency-secret",
                LocalDateTime.of(2026, 7, 7, 9, 0)
        );
        obligation.failAttempt(
                attempt,
                "toss-payout-secret",
                "INVALID_ACCOUNT",
                LocalDateTime.of(2026, 7, 7, 9, 0, 3),
                false
        );
        payoutObligationRepository.save(obligation);
    }

    private void completePayoutAttempt(ConfirmedProjectSettlement confirmed) {
        PayoutObligation obligation = payoutObligationRepository.findById(confirmed.payoutObligationId())
                .orElseThrow();
        PayoutAttempt attempt = obligation.startAttempt(
                "ref-completed-payout",
                "completed-idempotency-key",
                LocalDateTime.of(2026, 7, 7, 9, 0)
        );
        obligation.completeAttempt(
                attempt,
                "toss-completed-payout",
                LocalDateTime.of(2026, 7, 7, 9, 0, 3)
        );
        payoutObligationRepository.save(obligation);
    }
}
