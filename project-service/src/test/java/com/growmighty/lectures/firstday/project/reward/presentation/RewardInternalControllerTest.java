package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * orderId는 (orderId, rewardId, operation) 멱등키의 일부라 필수값이다(#195) — 누락 시 서비스까지
 * 가지 않고 400으로 거부되는지 확인한다.
 */
@WebMvcTest(RewardInternalController.class)
class RewardInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RewardService rewardService;

    @Test
    @DisplayName("decrease-stock: orderId가 없으면 400으로 거부되고 서비스는 호출되지 않는다")
    void decreaseStock_missingOrderId_rejectedWith400() throws Exception {
        mockMvc.perform(post("/internal/v1/rewards/{rewardId}/decrease-stock", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rewardService);
    }

    @Test
    @DisplayName("restore-stock: orderId가 없으면 400으로 거부되고 서비스는 호출되지 않는다")
    void restoreStock_missingOrderId_rejectedWith400() throws Exception {
        mockMvc.perform(post("/internal/v1/rewards/{rewardId}/restore-stock", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rewardService);
    }
}
