package com.growmighty.lectures.firstday.settlement.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProjectSettlementControllerTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("프로젝트 정산 기준일 전에도 내부 API로 대상 월의 프로젝트 정산을 실행한다")
    void runsProjectSettlementsManually() throws Exception {
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
                .andExpect(jsonPath("$.data.confirmedSettlements[0].projectId").value(9_000_001))
                .andExpect(jsonPath("$.data.confirmedSettlements[0].creatorPayoutAmount").value(91_200))
                .andExpect(jsonPath("$.data.confirmedSettlements[0].scheduledDate").value("2026-08-03"))
                .andExpect(jsonPath("$.data.confirmedSettlements[0].payoutObligationStatus")
                        .value("SCHEDULED"));
    }

    @Test
    @DisplayName("수동 실행 요청에 프로젝트 정산 대상 월이 없으면 거부한다")
    void rejectsManualRunWithoutSettlementMonth() throws Exception {
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"))
                .andExpect(jsonPath("$.error.errors[?(@.field == 'settlementMonth')]").exists());
    }
}
