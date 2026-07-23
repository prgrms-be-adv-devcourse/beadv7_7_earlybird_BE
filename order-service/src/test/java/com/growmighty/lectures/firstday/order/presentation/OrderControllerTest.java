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
import java.util.List;

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
                1L, OrderStatus.PAID, BigDecimal.valueOf(20_000), BigDecimal.ZERO, BigDecimal.valueOf(20_000),
                "김하나한", "010-0000-0000", "서울시 강남구", "06236"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"requests":[{"rewardId":1,"quantity":1}],"receiverName":"김하나한","receiverPhone":"010-0000-0000","shippingAddress":"서울시 강남구","zipCode":"06236"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("내 후원 내역은 GET /orders/me 로 userId 기준 조회한다")
    void getMyOrders_success() throws Exception {
        when(orderApiService.getOrdersByUser(1L)).thenReturn(List.of(new OrderResult(
                1L, OrderStatus.PAID, BigDecimal.valueOf(20_000), BigDecimal.ZERO, BigDecimal.valueOf(20_000),
                "김하나한", "010-0000-0000", "서울시 강남구", "06236")));

        mockMvc.perform(get("/orders/me").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("후원 상세는 GET /orders/{orderId} 로 조회한다")
    void getOrder_success() throws Exception {
        when(orderApiService.getOrderInfo(1L)).thenReturn(new OrderResult(
                1L, OrderStatus.PAID, BigDecimal.valueOf(20_000), BigDecimal.ZERO, BigDecimal.valueOf(20_000),
                "김하나한", "010-0000-0000", "서울시 강남구", "06236"));

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회는 404를 반환한다")
    void inspectOrder_notFound_404() throws Exception {
        when(orderApiService.getOrderInfo(999L))
                .thenThrow(new EntityNotFoundException("존재하지 않는 주문입니다. orderId=999"));

        mockMvc.perform(get("/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("존재하지 않는 주문입니다. orderId=999"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("비즈니스 상태 위반(이미 취소)은 409를 반환한다")
    void cancelOrder_conflict_409() throws Exception {
        when(orderApiService.cancelOrder(1L))
                .thenThrow(new IllegalStateException("이미 취소된 주문입니다."));

        mockMvc.perform(post("/orders/1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("이미 취소된 주문입니다."));
    }

    @Test
    @DisplayName("잘못된 JSON 본문은 프레임워크 기본 처리를 거쳐도 success/data/error 봉투를 반환한다")
    void placeOrder_malformedBody_400() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("검증 실패(@Valid)는 400과 필드별 오류 목록을 반환한다")
    void placeOrder_validationFailure_400() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requests":[{"rewardId":1,"quantity":-1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.errors[?(@.field == 'userId')]").exists())
                .andExpect(jsonPath("$.error.errors[?(@.field == 'requests[0].quantity')]").exists());
    }
}
