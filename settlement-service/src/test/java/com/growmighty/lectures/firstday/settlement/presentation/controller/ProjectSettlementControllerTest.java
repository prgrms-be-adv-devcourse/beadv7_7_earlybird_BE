// TODO(settlement-plan): Verify manual requests invoke the same idempotent monthly-run interface as scheduling.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectSettlementControllerTest extends MySqlIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataOrderPaymentFactRepository orderPaymentFactRepository;

    @Test
    @DisplayName("내부 API가 대상 월 결제별 PG 대사를 실행한다")
    void runsPaymentReconciliationManually() throws Exception {
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
                .andExpect(jsonPath("$.data.confirmedOrderIds[0]").value(9_000_001_001L));

        assertThat(orderPaymentFactRepository.findById(9_000_001_001L).orElseThrow().reconciliationStatus())
                .isEqualTo(OrderPaymentFact.ReconciliationStatus.CONFIRMED);
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

}
