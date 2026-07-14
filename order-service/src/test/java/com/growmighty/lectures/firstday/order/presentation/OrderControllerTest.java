package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderApiService orderApiService;

    @Test
    @DisplayName("주문 생성 성공 시 success=true 와 data를 감싼 응답을 반환한다")
    void placeOrder_success_envelope() throws Exception {
        when(orderApiService.placeOrder(any())).thenReturn(new OrderResult(
                1L, OrderStatus.PAID, BigDecimal.valueOf(20_000), BigDecimal.ZERO, BigDecimal.valueOf(20_000)));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"requests":[{"productId":1,"quantity":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회는 404와 ENTITY_NOT_FOUND 코드를 반환한다")
    void inspectOrder_notFound_404() throws Exception {
        when(orderApiService.inspectOrder(999L))
                .thenThrow(new EntityNotFoundException("존재하지 않는 주문입니다. orderId=999"));

        mockMvc.perform(get("/orders/999/inspect"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C003"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("비즈니스 상태 위반(이미 취소)은 409와 INVALID_STATE 코드를 반환한다")
    void cancelOrder_conflict_409() throws Exception {
        when(orderApiService.cancelOrder(1L))
                .thenThrow(new IllegalStateException("이미 취소된 주문입니다."));

        mockMvc.perform(post("/orders/1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C002"));
    }

    @Test
    @DisplayName("잘못된 JSON 본문은 400과 INVALID_INPUT 코드를 반환한다")
    void placeOrder_malformedBody_400() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
    }
}
