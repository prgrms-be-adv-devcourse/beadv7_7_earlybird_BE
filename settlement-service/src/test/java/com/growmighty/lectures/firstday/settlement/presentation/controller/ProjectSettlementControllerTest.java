// TODO(settlement-plan): Verify manual requests invoke the same idempotent monthly-run interface as scheduling.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ProjectSettlementControllerTest.CommonBusinessErrorController.class)
class ProjectSettlementControllerTest extends MySqlIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataProjectOutcomeFactRepository projectOutcomeFactRepository;

    @Autowired
    private SpringDataOrderPaymentFactRepository orderPaymentFactRepository;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

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
    @DisplayName("프로젝트 정산 기준일 전에도 내부 API로 대상 월의 프로젝트 정산을 실행한다")
    void runsProjectSettlementsManually() throws Exception {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                9_000_001L,
                "seller-9000001",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********0001",
                LocalDateTime.of(2026, 7, 23, 9, 0)
        ));
        projectOutcomeFactRepository.save(ProjectOutcomeFact.of(
                9_000_001L,
                9_000_001L,
                ProjectOutcomeFact.Outcome.SUCCEEDED,
                Instant.parse("2026-07-23T10:00:00Z")
        ));
        orderPaymentFactRepository.save(OrderPaymentFact.completed(
                9_000_001_001L,
                "pg-9000001",
                9_000_001L,
                Money.wons(100_000),
                Instant.parse("2026-07-15T10:00:00Z")
        ));
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.settlementMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.projectResults[0].projectId").value(9_000_001))
                .andExpect(jsonPath("$.data.projectResults[0].outcomeStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.projectResults[0].processingStatus")
                        .value("SETTLEMENT_CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedSettlements[0].projectId").value(9_000_001))
                .andExpect(jsonPath("$.data.confirmedSettlements[0].creatorPayoutAmount").value(91_200))
                .andExpect(jsonPath("$.data.confirmedSettlements[0].scheduledDate").value("2026-08-03"))
                .andExpect(jsonPath("$.data.confirmedSettlements[0].payoutStatus")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.data.confirmedSettlements[0].payoutObligationId").doesNotExist());
    }

    @Test
    @DisplayName("수동 실행 요청에 프로젝트 정산 대상 월이 없으면 거부한다")
    void rejectsManualRunWithoutSettlementMonth() throws Exception {
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.errors[?(@.field == 'settlementMonth')]").exists());
    }

    @Test
    @DisplayName("common 비즈니스 오류가 공통 오류 응답 형식을 유지한다")
    void preservesCommonBusinessErrorEnvelope() throws Exception {
        mockMvc.perform(get("/test/common-business-error"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message")
                        .value("일시적으로 프로젝트 정산을 실행할 수 없습니다."));
    }

    @RestController
    static class CommonBusinessErrorController {

        @GetMapping("/test/common-business-error")
        void fail() {
            throw new ServiceUnavailableException("일시적으로 프로젝트 정산을 실행할 수 없습니다.");
        }
    }

}
