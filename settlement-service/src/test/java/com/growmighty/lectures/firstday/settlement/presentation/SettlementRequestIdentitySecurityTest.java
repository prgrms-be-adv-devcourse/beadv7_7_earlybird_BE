package com.growmighty.lectures.firstday.settlement.presentation;

import static com.growmighty.lectures.firstday.settlement.presentation.TestJwtTokens.bearerToken;
import static com.growmighty.lectures.firstday.settlement.presentation.TestJwtTokens.expiredBearerToken;
import static com.growmighty.lectures.firstday.settlement.presentation.TestJwtTokens.invalidSignatureBearerToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.settlement.config.SettlementSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
        controllers = SettlementRequestIdentitySecurityTest.SecurityProbeController.class,
        properties = "jwt.secret=A196b/7T15tWsckvVi3uwbzkfgbxZnvVYHTQ5kl+6nQ="
)
@Import({
        SettlementSecurityConfig.class,
        SettlementRequestIdentitySecurityTest.SecurityProbeController.class
})
class SettlementRequestIdentitySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("JWT와 Gateway 전달 헤더의 사용자·역할이 모두 일치하면 창작자 정산 조회를 허용한다")
    void allowsCreatorRequestWithMatchingJwtAndHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("42", "CREATOR"))
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효한 JWT라도 Gateway 전달 헤더가 없으면 정산 조회 경계에서 거부한다")
    void rejectsValidJwtWithoutForwardedIdentityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("42", "CREATOR")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("유효한 사용자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("JWT 없이 Gateway 전달 헤더만 위조하면 정산 조회 경계에서 거부한다")
    void rejectsSpoofedHeadersWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("유효한 사용자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("JWT subject와 Gateway 사용자 식별자 헤더가 다르면 거부한다")
    void rejectsMismatchedUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("42", "CREATOR"))
                        .header("X-User-Id", "99")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("유효한 사용자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("JWT role과 Gateway 역할 헤더가 다르면 거부한다")
    void rejectsMismatchedRoleHeader() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("42", "CREATOR"))
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("유효한 사용자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("동일한 Gateway 사용자 식별자 헤더가 중복되어도 신뢰하지 않는다")
    void rejectsDuplicateUserIdHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("42", "CREATOR"))
                        .header("X-User-Id", "42", "42")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만료된 JWT와 일치하는 전달 헤더를 함께 보내도 거부한다")
    void rejectsExpiredJwtWithMatchingHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, expiredBearerToken("CREATOR"))
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("유효한 사용자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("서명이 다른 JWT와 일치하는 전달 헤더를 함께 보내도 거부한다")
    void rejectsJwtWithInvalidSignature() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, invalidSignatureBearerToken("42", "CREATOR"))
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("유효한 사용자 인증이 필요합니다."));
    }

    @Test
    @DisplayName("검증된 후원자 역할은 창작자 정산 조회 권한이 없다")
    void rejectsBackerFromCreatorSettlementPath() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("42", "BACKER"))
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "BACKER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("창작자 권한이 필요합니다."));
    }

    @Test
    @DisplayName("관리자 JWT와 전달 헤더가 일치하면 관리자 상세 경로를 허용한다")
    void allowsAdminRequestWithMatchingJwtAndHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/all/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("7", "ADMIN"))
                        .header("X-User-Id", "7")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Gateway가 관리자 상세 경로를 놓쳐도 Settlement가 창작자 접근을 거부한다")
    void rejectsCreatorFromAdminSettlementDetailPath() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/all/security-probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("42", "CREATOR"))
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "CREATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("관리자 권한이 필요합니다."));
    }

    @RestController
    static class SecurityProbeController {

        @GetMapping("/api/v1/settlements/security-probe")
        void creatorProbe() {
        }

        @GetMapping("/api/v1/settlements/all/security-probe")
        void adminProbe() {
        }
    }
}
