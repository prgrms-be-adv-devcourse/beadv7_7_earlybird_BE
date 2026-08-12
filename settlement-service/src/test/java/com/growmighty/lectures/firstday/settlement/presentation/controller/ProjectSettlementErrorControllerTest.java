// TODO(settlement-plan): Cover validation, reconciliation review, and event-contract errors through the HTTP interface.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectSettlementErrorControllerTest extends MySqlIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

    @Autowired
    private SpringDataProjectOutcomeFactRepository projectOutcomeFactRepository;

    @Autowired
    private SpringDataOrderPaymentFactRepository orderPaymentFactRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSettlementInputs() {
        jdbcTemplate.execute("UPDATE project_settlements SET successful_attempt_id = NULL");
        jdbcTemplate.execute("DELETE FROM payout_attempts");
        jdbcTemplate.execute("DELETE FROM project_settlements");
        jdbcTemplate.execute("DELETE FROM order_payment_facts");
        jdbcTemplate.execute("DELETE FROM project_outcome_facts");
        jdbcTemplate.execute("DELETE FROM creator_payout_profiles");
    }

    @Test
    @DisplayName("지급 프로필이 준비되지 않은 프로젝트 정산은 Settlement 오류로 응답한다")
    void rejectsSettlementWhenPayoutProfileIsNotReady() throws Exception {
        storeSucceededProject(91L, 91L, 100_000);

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("창작자 지급 준비가 완료되지 않았습니다."));
    }

    @Test
    @DisplayName("지급 프로필이 승인 대기 중이면 지급 준비 미완료 오류로 응답한다")
    void rejectsSettlementWhenPayoutProfileAwaitsApproval() throws Exception {
        long creatorId = 96L;
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                creatorId,
                "seller-96",
                CreatorPayoutStatus.APPROVAL_REQUIRED,
                "088",
                "********0096",
                LocalDateTime.of(2026, 7, 23, 9, 0)
        ));
        storeSucceededProject(96L, creatorId, 100_000);

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("창작자 지급 준비가 완료되지 않았습니다."));
    }

    @Test
    @DisplayName("Order가 완전한 주문 결제 금액을 제공하지 못하면 재시도 가능한 오류로 응답한다")
    void rejectsSettlementWhenOrderPaymentInputsAreUnavailable() throws Exception {
        long creatorId = 92L;
        creatorPayoutProfileRepository.save(payoutReadyProfile(creatorId));
        projectOutcomeFactRepository.save(ProjectOutcomeFact.of(
                92L, creatorId, ProjectOutcomeFact.Outcome.SUCCEEDED, Instant.parse("2026-07-23T10:00:00Z")
        ));

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("주문 결제금액을 확인할 수 없습니다."));
    }

    @Test
    @DisplayName("저장된 프로젝트 정산 원본의 정합성 오류는 내부 정보를 노출하지 않는다")
    void hidesPersistenceIntegrityFailureDetails() throws Exception {
        long projectId = 95L;
        long creatorId = 95L;
        LocalDateTime recordedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        jdbcTemplate.update("""
                        INSERT INTO project_settlements (
                            project_id,
                            creator_id,
                            payment_and_settlement_agency_fee_rate,
                            platform_fee_rate,
                            fee_vat_rate,
                            base_amount,
                            agency_fee_amount,
                            agency_fee_vat_amount,
                            platform_fee_amount,
                            platform_fee_vat_amount,
                            other_deduction_amount,
                            creator_payout_amount,
                            destination_toss_seller_id,
                            destination_bank_code,
                            destination_masked_account_number,
                            scheduled_date,
                            status,
                            confirmed_at,
                            version,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                projectId,
                creatorId,
                0.04,
                0.04,
                0.10,
                100_000,
                4_000,
                400,
                4_000,
                400,
                0,
                99_999,
                "seller-95",
                "088",
                "********0095",
                LocalDate.of(2026, 8, 3),
                "SCHEDULED",
                recordedAt,
                0,
                recordedAt,
                recordedAt
        );
        storeSucceededProject(projectId, creatorId, 100_000);

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 데이터가 일치하지 않습니다."))
                .andExpect(content().string(not(containsString("창작자 지급액이 공제 후 금액과 일치하지 않습니다"))));
    }

    private void storeSucceededProject(long projectId, long creatorId, long amount) {
        projectOutcomeFactRepository.save(ProjectOutcomeFact.of(
                projectId, creatorId, ProjectOutcomeFact.Outcome.SUCCEEDED, Instant.parse("2026-07-23T10:00:00Z")
        ));
        orderPaymentFactRepository.save(OrderPaymentFact.completed(
                projectId * 1_000, "pg-" + projectId, projectId, Money.wons(amount), Instant.parse("2026-07-15T10:00:00Z")
        ));
    }

    private static CreatorPayoutProfile payoutReadyProfile(Long creatorId) {
        return CreatorPayoutProfile.registered(
                creatorId,
                "seller-" + creatorId,
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********" + creatorId,
                LocalDateTime.of(2026, 7, 23, 9, 0)
        );
    }
}
