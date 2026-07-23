package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProjectController가 더 이상 ApiResponse를 직접 만들지 않고 plain DTO를 반환해도
 * ApiResponseWrappingAdvice가 응답을 감싸는지 검증한다 (이슈 #52 회귀 테스트).
 */
@WebMvcTest(ProjectController.class)
class ProjectControllerEnvelopeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Test
    @DisplayName("plain DTO를 반환해도 advice가 success/data/error 봉투로 감싼다")
    void findById_wrapsPlainDtoInEnvelope() throws Exception {
        ProjectResponse response = new ProjectResponse(
                1L, 10L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), BigDecimal.ZERO,
                LocalDateTime.now(), LocalDate.now().plusDays(30),
                "IN_PROGRESS", false, null, null, null, null, null, null);
        when(projectService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/projects/{projectId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.title").value("title"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("Void를 반환하는 엔드포인트는 data: null 봉투로 감싸진다")
    void delete_wrapsNullDataInEnvelope() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/{projectId}", 1L)
                        .header(JwtHeaders.USER_ID, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
