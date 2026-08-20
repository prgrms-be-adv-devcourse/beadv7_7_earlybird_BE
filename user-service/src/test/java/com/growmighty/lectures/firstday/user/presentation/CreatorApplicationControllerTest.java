package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.user.application.CreatorApplicationService;
import com.growmighty.lectures.firstday.user.application.dto.CreatorApplicationInfo;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreatorApplicationController.class)
class CreatorApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatorApplicationService creatorApplicationService;

    private static final CreatorApplicationInfo PENDING = new CreatorApplicationInfo(
            10L, 1L, "창작자", "테크", "소개글", null, null,
            "신한은행", "88", "110-123-456789", "창작자", CreatorApplicationStatus.PENDING, null);

    private static String validRequestJson() {
        return "{\"creatorName\":\"창작자\",\"category\":\"테크\",\"introduction\":\"소개글\","
                + "\"bankCode\":\"88\",\"accountNumber\":\"110-123-456789\",\"accountHolder\":\"창작자\"}";
    }

    @Test
    @DisplayName("POST /api/v1/users/me/creator-application 은 필수 필드가 빈 값이면 400 을 반환한다")
    void apply_withBlankFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/creator-application")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorName\":\"\",\"category\":\"\",\"introduction\":\"\","
                                + "\"bankCode\":\"\",\"accountNumber\":\"\",\"accountHolder\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/creator-application 은 정상 요청이면 PENDING 신청 정보를 반환한다")
    void apply_withValidRequest_returnsPendingApplication() throws Exception {
        when(creatorApplicationService.apply(any())).thenReturn(PENDING);

        mockMvc.perform(post("/api/v1/users/me/creator-application")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/users/creator-applications 는 ADMIN 이 아니면 400 을 반환한다")
    void findAll_withNonAdminRole_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/creator-applications")
                        .header(JwtHeaders.USER_ROLE, "BACKER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/users/creator-applications 는 ADMIN 이면 신청 목록을 반환한다")
    void findAll_withAdminRole_returnsApplications() throws Exception {
        when(creatorApplicationService.findAll(isNull())).thenReturn(List.of(PENDING));

        mockMvc.perform(get("/api/v1/users/creator-applications")
                        .header(JwtHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/users/creator-applications?status= 는 필터 조건을 서비스로 그대로 전달한다")
    void findAll_withStatusFilter_delegatesToService() throws Exception {
        when(creatorApplicationService.findAll(eq(CreatorApplicationStatus.PENDING))).thenReturn(List.of(PENDING));

        mockMvc.perform(get("/api/v1/users/creator-applications?status=PENDING")
                        .header(JwtHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/users/creator-applications/{id}/approve 는 ADMIN 이 아니면 400 을 반환한다")
    void approve_withNonAdminRole_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/creator-applications/10/approve")
                        .header(JwtHeaders.USER_ROLE, "CREATOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/users/creator-applications/{id}/approve 는 ADMIN 이면 APPROVED 신청 정보를 반환한다")
    void approve_withAdminRole_returnsApprovedApplication() throws Exception {
        CreatorApplicationInfo approved = new CreatorApplicationInfo(
                10L, 1L, "창작자", "테크", "소개글", null, null,
                "신한은행", "88", "110-123-456789", "창작자", CreatorApplicationStatus.APPROVED, null);
        when(creatorApplicationService.approve(10L)).thenReturn(approved);

        mockMvc.perform(post("/api/v1/users/creator-applications/10/approve")
                        .header(JwtHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /api/v1/users/creator-applications/{id}/reject 는 reason 이 없으면 400 을 반환한다")
    void reject_withBlankReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/creator-applications/10/reject")
                        .header(JwtHeaders.USER_ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/users/creator-applications/{id}/reject 는 ADMIN 이면 REJECTED 신청 정보를 반환한다")
    void reject_withAdminRole_returnsRejectedApplication() throws Exception {
        CreatorApplicationInfo rejected = new CreatorApplicationInfo(
                10L, 1L, "창작자", "테크", "소개글", null, null,
                "신한은행", "88", "110-123-456789", "창작자", CreatorApplicationStatus.REJECTED, "서류 미비");
        when(creatorApplicationService.reject(any())).thenReturn(rejected);

        mockMvc.perform(post("/api/v1/users/creator-applications/10/reject")
                        .header(JwtHeaders.USER_ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"서류 미비\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("서류 미비"));
    }
}
