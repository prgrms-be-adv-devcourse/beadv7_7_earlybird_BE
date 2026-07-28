package com.growmighty.lectures.firstday.settlement.presentation;

import static com.growmighty.lectures.firstday.settlement.presentation.TestJwtTokens.adminBearerToken;
import static com.growmighty.lectures.firstday.settlement.presentation.TestJwtTokens.bearerToken;
import static com.growmighty.lectures.firstday.settlement.presentation.TestJwtTokens.expiredBearerToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ProjectSettlementControllerTest.CommonBusinessErrorController.class)
class ProjectSettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("프로젝트 정산 기준일 전에도 내부 API로 대상 월의 프로젝트 정산을 실행한다")
    void runsProjectSettlementsManually() throws Exception {
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .header(HttpHeaders.AUTHORIZATION, adminBearerToken())
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
    @DisplayName("관리자 인증 없이 프로젝트 정산 수동 실행을 요청하면 거부한다")
    void rejectsManualRunWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("유효한 관리자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("직접 호출에서 관리자 역할 헤더만 위조해도 프로젝트 정산 수동 실행을 거부한다")
    void rejectsManualRunWithSpoofedAdminHeader() throws Exception {
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("유효한 관리자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("창작자 권한으로 프로젝트 정산 수동 실행을 요청하면 거부한다")
    void rejectsManualRunFromCreator() throws Exception {
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("CREATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("관리자 권한이 필요합니다."));
    }

    @Test
    @DisplayName("만료된 관리자 JWT로 프로젝트 정산 수동 실행을 요청하면 거부한다")
    void rejectsManualRunWithExpiredAdminToken() throws Exception {
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .header(HttpHeaders.AUTHORIZATION, expiredBearerToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("유효한 관리자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("수동 실행 요청에 프로젝트 정산 대상 월이 없으면 거부한다")
    void rejectsManualRunWithoutSettlementMonth() throws Exception {
        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .header(HttpHeaders.AUTHORIZATION, adminBearerToken())
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
