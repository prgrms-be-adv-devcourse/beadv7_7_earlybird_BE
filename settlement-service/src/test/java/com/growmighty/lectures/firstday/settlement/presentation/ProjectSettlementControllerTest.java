package com.growmighty.lectures.firstday.settlement.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(ProjectSettlementControllerTest.CommonBusinessErrorController.class)
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

    @Test
    @DisplayName("common 비즈니스 오류가 공통 오류 응답 형식을 유지한다")
    void preservesCommonBusinessErrorEnvelope() throws Exception {
        mockMvc.perform(get("/test/common-business-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C002"))
                .andExpect(jsonPath("$.error.message").value("처리할 수 없는 상태입니다."));
    }

    @RestController
    static class CommonBusinessErrorController {

        @GetMapping("/test/common-business-error")
        void fail() {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
    }
}
