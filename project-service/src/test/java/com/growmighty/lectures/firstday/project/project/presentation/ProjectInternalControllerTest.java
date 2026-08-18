package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * order-service가 결제 확정/취소 시 push로 호출하는 경로 — 정상 위임과, 음수/누락 값이
 * 서비스까지 가지 않고 400으로 거부되는지 확인한다.
 */
@WebMvcTest(ProjectInternalController.class)
class ProjectInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Test
    @DisplayName("fundedAmount: 정상 요청이면 서비스에 그대로 위임한다")
    void updateFundedAmount_delegatesToService() throws Exception {
        mockMvc.perform(put("/internal/v1/projects/{projectId}/funded-amount", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundedAmount\": 500000}"))
                .andExpect(status().isNoContent());

        verify(projectService).updateFundedAmount(eq(1L), eq(BigDecimal.valueOf(500000)));
    }

    @Test
    @DisplayName("fundedAmount: 음수면 400으로 거부되고 서비스는 호출되지 않는다")
    void updateFundedAmount_negative_rejectedWith400() throws Exception {
        mockMvc.perform(put("/internal/v1/projects/{projectId}/funded-amount", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundedAmount\": -1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("fundedAmount: 누락되면 400으로 거부되고 서비스는 호출되지 않는다")
    void updateFundedAmount_missing_rejectedWith400() throws Exception {
        mockMvc.perform(put("/internal/v1/projects/{projectId}/funded-amount", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectService);
    }
}
