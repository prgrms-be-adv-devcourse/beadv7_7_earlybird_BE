package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.order.application.InternalOrderApiService;
import com.growmighty.lectures.firstday.order.application.dto.OrderVerificationResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalOrderController.class)
class InternalOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalOrderApiService internalOrderApiService;

    @Disabled
    @Test
    @DisplayName("해당 project에 order 기록 있을 시 true 반환")
    void hasOrderedReward_true() throws Exception {
        when(internalOrderApiService.hasOrderedReward(100L)).thenReturn(true);

        mockMvc.perform(get("/internal/v1/orders/100/ordered-existence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.data.projectId").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(internalOrderApiService).hasOrderedReward(100L);
    }

    @Disabled
    @Test
    @DisplayName("해당 project에 order 기록 있을 시 false 반환")
    void hasOrderedReward_false() throws Exception {
        when(internalOrderApiService.hasOrderedReward(200L)).thenReturn(false);

        mockMvc.perform(get("/internal/v1/orders/200/ordered-existence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(internalOrderApiService).hasOrderedReward(200L);
    }

    @Disabled
    @Test
    @DisplayName("purchase verification returns verified flag and reward name")
    void getOrderedVerification() throws Exception {
        when(internalOrderApiService.getOrderedVerification(1L, 10L))
                .thenReturn(OrderVerificationResult.verified("Reward A"));

        mockMvc.perform(get("/internal/v1/orders/purchase-verification")
                        .param("userId", "1")
                        .param("rewardId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.rewardName").value("Reward A"))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(internalOrderApiService).getOrderedVerification(1L, 10L);
    }

    @Disabled
    @Test
    void updatePaymentStatus() throws Exception {
        mockMvc.perform(put("/internal/v1/orders/1/payment-status")
                        .contentType("application/json")
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk());

        verify(internalOrderApiService).applyPaymentStatus(1L, "PAID");
    }
}
