package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.order.application.OrderApiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalOrderController.class)
class InternalOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderApiService orderApiService;

    @Test
    @DisplayName("해당 project에 order 기록 있을 시 true 반환")
    void hasOrderedReward_true() throws Exception {
        when(orderApiService.hasOrderedReward(100L)).thenReturn(true);

        mockMvc.perform(get("/internal/v1/orders/100/ordered-existence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.data.success").doesNotExist())
                .andExpect(jsonPath("$.data.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(orderApiService).hasOrderedReward(100L);
    }

    @Test
    @DisplayName("해당 project에 order 기록 있을 시 false 반환")
    void hasOrderedReward_false() throws Exception {
        when(orderApiService.hasOrderedReward(200L)).thenReturn(false);

        mockMvc.perform(get("/internal/v1/orders/200/ordered-existence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false))
                .andExpect(jsonPath("$.data.success").doesNotExist())
                .andExpect(jsonPath("$.data.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(orderApiService).hasOrderedReward(200L);
    }
}
